package in.stocktrace.broker;

import in.stocktrace.audit.OrderAuditService;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Fan-out engine: places the same broker order on every active user in parallel
 * (best-effort). Per-user failures are isolated and recorded to the audit log.
 */
@Service
public class OrderFanoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderFanoutService.class);

    private final KiteUserService userService;
    private final BrokerRegistry brokerRegistry;
    private final OrderAuditService audit;
    private final Executor executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "stocktrace-fanout");
        t.setDaemon(true);
        return t;
    });

    public OrderFanoutService(KiteUserService userService,
                              BrokerRegistry brokerRegistry,
                              OrderAuditService audit) {
        this.userService = userService;
        this.brokerRegistry = brokerRegistry;
        this.audit = audit;
    }

    public List<BrokerOrderResult> placeForAllActive(String source, BrokerOrderRequest request) {
        List<KiteUser> active = userService.listActive();
        if (active.isEmpty()) {
            log.info("[{}] fan-out skipped: no active users", source);
            return List.of();
        }
        log.info("[{}] placing order {} on {} active user(s)", source, request.tradingsymbol(), active.size());

        BrokerService broker = brokerRegistry.getDefault();

        List<CompletableFuture<BrokerOrderResult>> futures = active.stream()
                .map(u -> CompletableFuture.supplyAsync(() -> {
                    BrokerOrderResult result;
                    try {
                        result = broker.placeOrder(u.getUserId(), request);
                    } catch (RuntimeException ex) {
                        log.warn("[{}] fan-out failed for user {}: {}", source, u.getUserId(), ex.getMessage());
                        result = BrokerOrderResult.fail(u.getUserId(), ex.getMessage());
                    }
                    audit.record(source, u.getUserId(), request, result);
                    return result;
                }, executor))
                .toList();

        return futures.stream().map(CompletableFuture::join).toList();
    }
}
