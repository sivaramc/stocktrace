package in.stocktrace.scheduler;

import in.stocktrace.broker.BrokerRegistry;
import in.stocktrace.broker.BrokerService;
import in.stocktrace.broker.OrderFanoutService;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerQuote;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Periodically polls the Kite Quote API for every active scan rule's instrument
 * and triggers fan-out orders when a rule's condition matches. Runs on the cron
 * configured in {@code stocktrace.scheduler.stock-scan.cron} (default: every
 * minute during IST market hours).
 */
@Component
public class StockScannerJob {

    private static final Logger log = LoggerFactory.getLogger(StockScannerJob.class);

    private final ScanRuleRepository rules;
    private final KiteUserService userService;
    private final BrokerRegistry brokerRegistry;
    private final OrderFanoutService fanout;
    private final boolean enabled;

    public StockScannerJob(ScanRuleRepository rules,
                           KiteUserService userService,
                           BrokerRegistry brokerRegistry,
                           OrderFanoutService fanout,
                           @Value("${stocktrace.scheduler.stock-scan.enabled:true}") boolean enabled) {
        this.rules = rules;
        this.userService = userService;
        this.brokerRegistry = brokerRegistry;
        this.fanout = fanout;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${stocktrace.scheduler.stock-scan.cron:0 */1 9-15 * * MON-FRI}",
               zone = "${stocktrace.scheduler.stock-scan.zone:Asia/Kolkata}")
    public void runScan() {
        if (!enabled) {
            return;
        }
        List<ScanRule> activeRules = rules.findAllByActiveTrue();
        if (activeRules.isEmpty()) {
            return;
        }

        List<KiteUser> activeUsers = userService.listActive();
        if (activeUsers.isEmpty()) {
            log.debug("scan skipped: no active users");
            return;
        }

        List<String> keys = activeRules.stream().map(ScanRule::instrumentKey).distinct().toList();
        BrokerService broker = brokerRegistry.getDefault();

        // Try each active user in turn until one successfully fetches quotes. A single
        // user with an expired token (deterministically first by PK order) must not
        // silently stall the scanner for everyone else.
        Map<String, BrokerQuote> quotes = null;
        for (KiteUser quoter : activeUsers) {
            try {
                quotes = broker.getQuotes(quoter.getUserId(), keys);
                break;
            } catch (RuntimeException ex) {
                log.warn("scan: quote fetch failed via user {}: {}", quoter.getUserId(), ex.getMessage());
            }
        }
        if (quotes == null) {
            log.warn("scan: no active user could fetch quotes; skipping cycle");
            return;
        }

        for (ScanRule rule : activeRules) {
            BrokerQuote q = quotes.get(rule.instrumentKey());
            if (q == null || q.lastPrice() == null) {
                continue;
            }
            if (rule.matches(q.lastPrice())) {
                log.info("scan: rule '{}' matched at LTP {}", rule.getName(), q.lastPrice());
                // One-shot semantics: deactivate and persist the rule BEFORE placing the
                // broker order. Spring Data repositories run save() in their own
                // transaction, so the deactivation is committed before any irrevocable
                // broker call — preventing duplicate orders if the subsequent fan-out
                // throws or the next scheduled run fires while the price still matches.
                rule.setLastTriggeredAt(Instant.now());
                rule.setActive(false);
                rules.save(rule);

                BrokerOrderRequest req = new BrokerOrderRequest(
                        rule.getTradingsymbol(),
                        rule.getExchange(),
                        rule.getTransactionType(),
                        rule.getOrderType(),
                        rule.getProduct(),
                        null,
                        null,
                        rule.getQuantity(),
                        null,
                        null,
                        null,
                        "scan-" + rule.getId()
                );
                fanout.placeForAllActive("SCAN:" + rule.getName(), req);
            }
        }
    }
}
