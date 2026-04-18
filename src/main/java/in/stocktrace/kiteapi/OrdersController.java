package in.stocktrace.kiteapi;

import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.Order;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import com.zerodhatech.models.Trade;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.zerodha.KiteConnectFactory;
import in.stocktrace.broker.zerodha.ZerodhaBrokerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kite/{userId}/orders")
public class OrdersController {

    private final KiteApiHelper kite;
    private final KiteConnectFactory factory;

    public OrdersController(KiteApiHelper kite, KiteConnectFactory factory) {
        this.kite = kite;
        this.factory = factory;
    }

    @GetMapping
    public List<Order> list(@PathVariable String userId) {
        return kite.call(userId, k -> k.getOrders());
    }

    @GetMapping("/trades")
    public List<Trade> trades(@PathVariable String userId) {
        return kite.call(userId, k -> k.getTrades());
    }

    @GetMapping("/{orderId}/history")
    public List<Order> history(@PathVariable String userId, @PathVariable String orderId) {
        return kite.call(userId, k -> k.getOrderHistory(orderId));
    }

    @GetMapping("/{orderId}/trades")
    public List<Trade> orderTrades(@PathVariable String userId, @PathVariable String orderId) {
        return kite.call(userId, k -> k.getOrderTrades(orderId));
    }

    @PostMapping
    public OrderResponse place(@PathVariable String userId,
                               @RequestParam(defaultValue = Constants.VARIETY_REGULAR) String variety,
                               @RequestBody BrokerOrderRequest body) {
        OrderParams params = ZerodhaBrokerService.toOrderParams(body, factory.user(userId));
        String v = body.variety() != null && !body.variety().isBlank() ? body.variety() : variety;
        return kite.call(userId, k -> k.placeOrder(params, v));
    }

    @PutMapping("/{variety}/{orderId}")
    public Order modify(@PathVariable String userId,
                        @PathVariable String variety,
                        @PathVariable String orderId,
                        @RequestBody BrokerOrderRequest body) {
        OrderParams params = ZerodhaBrokerService.toOrderParams(body, factory.user(userId));
        return kite.call(userId, k -> k.modifyOrder(orderId, params, variety));
    }

    @DeleteMapping("/{variety}/{orderId}")
    public Order cancel(@PathVariable String userId,
                        @PathVariable String variety,
                        @PathVariable String orderId,
                        @RequestParam(required = false) String parentOrderId) {
        if (parentOrderId != null && !parentOrderId.isBlank()) {
            return kite.call(userId, k -> k.cancelOrder(orderId, parentOrderId, variety));
        }
        return kite.call(userId, k -> k.cancelOrder(orderId, variety));
    }
}
