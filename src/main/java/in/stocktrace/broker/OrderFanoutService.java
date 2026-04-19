package in.stocktrace.broker;

import in.stocktrace.audit.OrderAuditService;
import in.stocktrace.broker.fivepaisa.FivePaisaUser;
import in.stocktrace.broker.fivepaisa.FivePaisaUserService;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Fan-out engine: places the same broker order on every active user in parallel
 * across every registered broker (best-effort). Per-user failures are isolated
 * and recorded to the audit log.
 */
@Service
public class OrderFanoutService {

    private static final Logger log = LoggerFactory.getLogger(OrderFanoutService.class);

    private final KiteUserService kiteUsers;
    private final FivePaisaUserService fivePaisaUsers;
    private final BrokerRegistry brokerRegistry;
    private final OrderAuditService audit;
    private final Executor executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "stocktrace-fanout");
        t.setDaemon(true);
        return t;
    });

    public OrderFanoutService(KiteUserService kiteUsers,
                              FivePaisaUserService fivePaisaUsers,
                              BrokerRegistry brokerRegistry,
                              OrderAuditService audit) {
        this.kiteUsers = kiteUsers;
        this.fivePaisaUsers = fivePaisaUsers;
        this.brokerRegistry = brokerRegistry;
        this.audit = audit;
    }

    /**
     * Fan-out to every active user on every registered broker. Order placement
     * runs in parallel. If a broker is not registered, its users are skipped.
     */
    public List<BrokerOrderResult> placeForAllActive(String source, BrokerOrderRequest request) {
        List<CompletableFuture<BrokerOrderResult>> futures = new ArrayList<>();

        if (brokerRegistry.isRegistered("zerodha")) {
            BrokerService zerodha = brokerRegistry.get("zerodha");
            for (KiteUser u : kiteUsers.listActive()) {
                futures.add(submit("zerodha", source, zerodha, u.getUserId(), request));
            }
        }

        if (brokerRegistry.isRegistered("5paisa")) {
            BrokerService fivePaisa = brokerRegistry.get("5paisa");
            for (FivePaisaUser u : fivePaisaUsers.listActive()) {
                futures.add(submit("5paisa", source, fivePaisa, u.getUserId(), request));
            }
        }

        if (futures.isEmpty()) {
            log.info("[{}] fan-out skipped: no active users across registered brokers", source);
            return List.of();
        }
        log.info("[{}] placing {} on {} active user(s) across brokers", source, request.tradingsymbol(), futures.size());
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private CompletableFuture<BrokerOrderResult> submit(String brokerId,
                                                        String source,
                                                        BrokerService broker,
                                                        String userId,
                                                        BrokerOrderRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            BrokerOrderResult result;
            try {
                result = broker.placeOrder(userId, request);
            } catch (RuntimeException ex) {
                log.warn("[{}] fan-out failed for {}/{}: {}", source, brokerId, userId, ex.getMessage());
                result = BrokerOrderResult.fail(userId, ex.getMessage());
            }
            try {
                audit.record(brokerId, source, userId, request, result);
            } catch (RuntimeException ex) {
                log.error("[{}] audit record failed for {}/{}: {}", source, brokerId, userId, ex.getMessage(), ex);
            }
            return result;
        }, executor);
    }
}
