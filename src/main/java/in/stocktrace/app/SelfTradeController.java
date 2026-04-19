package in.stocktrace.app;

import in.stocktrace.app.security.AppUserPrincipal;
import in.stocktrace.audit.OrderAuditService;
import in.stocktrace.broker.BrokerRegistry;
import in.stocktrace.broker.BrokerService;
import in.stocktrace.broker.fivepaisa.FivePaisaBrokerService;
import in.stocktrace.broker.fivepaisa.FivePaisaUser;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import in.stocktrace.user.KiteUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Self-trade endpoint. Places a BUY or SELL on the authenticated user's own
 * broker accounts only (never fan-out to other users). This is what the UI
 * BUY/SELL buttons call.
 */
@RestController
@RequestMapping("/api/trade")
public class SelfTradeController {

    private static final Logger log = LoggerFactory.getLogger(SelfTradeController.class);

    private final AppUserService appUsers;
    private final BrokerRegistry brokers;
    private final OrderAuditService audit;

    public SelfTradeController(AppUserService appUsers,
                               BrokerRegistry brokers,
                               OrderAuditService audit) {
        this.appUsers = appUsers;
        this.brokers = brokers;
        this.audit = audit;
    }

    @PostMapping("/buy")
    public TradeResponse buy(@AuthenticationPrincipal AppUserPrincipal me,
                             @Valid @RequestBody TradeRequest req) {
        return trade(me, req, "BUY");
    }

    @PostMapping("/sell")
    public TradeResponse sell(@AuthenticationPrincipal AppUserPrincipal me,
                              @Valid @RequestBody TradeRequest req) {
        return trade(me, req, "SELL");
    }

    private TradeResponse trade(AppUserPrincipal me, TradeRequest req, String side) {
        List<BrokerOrderResult> results = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        Optional<KiteUser> kite = appUsers.kiteUserOwnedBy(me.getId());
        if (kite.isPresent() && kite.get().isActive() && brokers.isRegistered("zerodha")) {
            BrokerService svc = brokers.get("zerodha");
            BrokerOrderRequest br = buildRequest(req, side);
            BrokerOrderResult r = place(svc, kite.get().getUserId(), br, "zerodha", "UI");
            audit.record("zerodha", "UI:" + me.getId(), kite.get().getUserId(), br, r);
            results.add(r);
        } else if (kite.isPresent() && !kite.get().isActive()) {
            skipped.add("zerodha (inactive — run TradeOn)");
        }

        Optional<FivePaisaUser> five = appUsers.fivePaisaUserOwnedBy(me.getId());
        if (five.isPresent() && five.get().isActive() && brokers.isRegistered("5paisa")) {
            if (!FivePaisaBrokerService.isValidScripCode(req.tradingsymbol())) {
                skipped.add("5paisa (tradingsymbol '" + req.tradingsymbol() + "' is not a numeric ScripCode)");
            } else {
                BrokerService svc = brokers.get("5paisa");
                BrokerOrderRequest br = buildRequest(req, side);
                BrokerOrderResult r = place(svc, five.get().getUserId(), br, "5paisa", "UI");
                audit.record("5paisa", "UI:" + me.getId(), five.get().getUserId(), br, r);
                results.add(r);
            }
        } else if (five.isPresent() && !five.get().isActive()) {
            skipped.add("5paisa (inactive — run TradeOn)");
        }

        return new TradeResponse(req.tradingsymbol(), side, results, skipped);
    }

    private BrokerOrderRequest buildRequest(TradeRequest req, String side) {
        return new BrokerOrderRequest(
                req.tradingsymbol(),
                req.exchange(),
                side,
                req.orderType(),
                req.product(),
                req.variety(),
                req.validity(),
                req.quantity(),
                req.price(),
                req.triggerPrice(),
                req.disclosedQuantity(),
                "UI"
        );
    }

    private BrokerOrderResult place(BrokerService svc, String brokerUserId,
                                    BrokerOrderRequest req, String brokerId, String source) {
        try {
            return svc.placeOrder(brokerUserId, req);
        } catch (RuntimeException ex) {
            log.warn("[{}] self-trade failed for {}/{}: {}", source, brokerId, brokerUserId, ex.getMessage());
            return BrokerOrderResult.fail(brokerUserId, ex.getMessage());
        }
    }

    public record TradeRequest(
            @NotBlank String tradingsymbol,
            String exchange,
            String orderType,
            String product,
            String variety,
            String validity,
            @Positive Integer quantity,
            Double price,
            Double triggerPrice,
            Integer disclosedQuantity
    ) {}

    public record TradeResponse(
            String tradingsymbol,
            String transactionType,
            List<BrokerOrderResult> results,
            List<String> skipped
    ) {}
}
