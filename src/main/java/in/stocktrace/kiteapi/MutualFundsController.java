package in.stocktrace.kiteapi;

import com.zerodhatech.models.MFHolding;
import com.zerodhatech.models.MFInstrument;
import com.zerodhatech.models.MFOrder;
import com.zerodhatech.models.MFSIP;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kite/{userId}/mf")
public class MutualFundsController {

    private final KiteApiHelper kite;

    public MutualFundsController(KiteApiHelper kite) {
        this.kite = kite;
    }

    @GetMapping("/instruments")
    public List<MFInstrument> instruments(@PathVariable String userId) {
        return kite.call(userId, k -> k.getMFInstruments());
    }

    @GetMapping("/orders")
    public List<MFOrder> orders(@PathVariable String userId) {
        return kite.call(userId, k -> k.getMFOrders());
    }

    @GetMapping("/orders/{orderId}")
    public MFOrder getOrder(@PathVariable String userId, @PathVariable String orderId) {
        return kite.call(userId, k -> k.getMFOrder(orderId));
    }

    @PostMapping("/orders")
    public MFOrder placeOrder(@PathVariable String userId, @RequestBody MfOrderRequest req) {
        return kite.call(userId, k -> k.placeMFOrder(
                req.tradingsymbol(),
                req.transactionType(),
                req.amount() == null ? 0d : req.amount(),
                req.quantity() == null ? 0d : req.quantity(),
                req.tag()));
    }

    @DeleteMapping("/orders/{orderId}")
    public Map<String, Boolean> cancelOrder(@PathVariable String userId, @PathVariable String orderId) {
        boolean ok = kite.call(userId, k -> k.cancelMFOrder(orderId));
        return Map.of("cancelled", ok);
    }

    @GetMapping("/sips")
    public List<MFSIP> sips(@PathVariable String userId) {
        return kite.call(userId, k -> k.getMFSIPs());
    }

    @GetMapping("/sips/{sipId}")
    public MFSIP getSip(@PathVariable String userId, @PathVariable String sipId) {
        return kite.call(userId, k -> k.getMFSIP(sipId));
    }

    @PostMapping("/sips")
    public MFSIP placeSip(@PathVariable String userId, @RequestBody MfSipRequest req) {
        return kite.call(userId, k -> k.placeMFSIP(
                req.tradingsymbol(),
                req.frequency(),
                req.instalmentDay(),
                req.instalments(),
                req.initialAmount() == null ? 0 : req.initialAmount(),
                req.amount() == null ? 0d : req.amount()));
    }

    @PutMapping("/sips/{sipId}")
    public Map<String, Boolean> modifySip(@PathVariable String userId,
                                          @PathVariable String sipId,
                                          @RequestBody MfSipModifyRequest req) {
        boolean ok = kite.call(userId, k -> k.modifyMFSIP(
                req.frequency(),
                req.day(),
                req.instalments(),
                req.amount() == null ? 0d : req.amount(),
                req.status(),
                sipId));
        return Map.of("modified", ok);
    }

    @DeleteMapping("/sips/{sipId}")
    public Map<String, Boolean> cancelSip(@PathVariable String userId, @PathVariable String sipId) {
        boolean ok = kite.call(userId, k -> k.cancelMFSIP(sipId));
        return Map.of("cancelled", ok);
    }

    @GetMapping("/holdings")
    public List<MFHolding> holdings(@PathVariable String userId) {
        return kite.call(userId, k -> k.getMFHoldings());
    }

    public record MfOrderRequest(
            String tradingsymbol,
            String transactionType,
            Double amount,
            Double quantity,
            String tag
    ) {}

    public record MfSipRequest(
            String tradingsymbol,
            String frequency,
            int instalmentDay,
            int instalments,
            Integer initialAmount,
            Double amount
    ) {}

    public record MfSipModifyRequest(
            String frequency,
            int day,
            int instalments,
            Double amount,
            String status
    ) {}
}
