package in.stocktrace.broker.zerodha;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.kiteconnect.utils.Constants;
import com.zerodhatech.models.OrderParams;
import com.zerodhatech.models.OrderResponse;
import com.zerodhatech.models.Quote;
import in.stocktrace.broker.BrokerService;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import in.stocktrace.broker.model.BrokerQuote;
import in.stocktrace.user.KiteUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZerodhaBrokerService implements BrokerService {

    private static final Logger log = LoggerFactory.getLogger(ZerodhaBrokerService.class);

    public static final String ID = "zerodha";

    private final KiteConnectFactory factory;

    public ZerodhaBrokerService(KiteConnectFactory factory) {
        this.factory = factory;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public BrokerOrderResult placeOrder(String brokerUserId, BrokerOrderRequest request) {
        try {
            KiteConnect kite = factory.forUser(brokerUserId);
            KiteUser user = factory.user(brokerUserId);
            OrderParams params = toOrderParams(request, user);
            String variety = firstNonBlank(request.variety(), user.getDefaultVariety(), Constants.VARIETY_REGULAR);
            OrderResponse resp = kite.placeOrder(params, variety);
            return BrokerOrderResult.ok(brokerUserId, resp == null ? null : resp.orderId);
        } catch (KiteException | RuntimeException | java.io.IOException ex) {
            log.warn("Zerodha placeOrder failed for user {}: {}", brokerUserId, ex.getMessage());
            return BrokerOrderResult.fail(brokerUserId, ex.getMessage());
        }
    }

    @Override
    public Map<String, BrokerQuote> getQuotes(String brokerUserId, List<String> instruments) {
        try {
            KiteConnect kite = factory.forUser(brokerUserId);
            Map<String, Quote> raw = kite.getQuote(instruments.toArray(new String[0]));
            Map<String, BrokerQuote> out = new LinkedHashMap<>();
            if (raw != null) {
                raw.forEach((k, q) -> out.put(k, new BrokerQuote(
                        k,
                        q.instrumentToken,
                        q.lastPrice,
                        q.ohlc != null ? q.ohlc.open : null,
                        q.ohlc != null ? q.ohlc.high : null,
                        q.ohlc != null ? q.ohlc.low : null,
                        q.ohlc != null ? q.ohlc.close : null,
                        (long) q.volumeTradedToday
                )));
            }
            return out;
        } catch (KiteException | RuntimeException | java.io.IOException ex) {
            log.warn("Zerodha getQuotes failed for user {}: {}", brokerUserId, ex.getMessage());
            throw new in.stocktrace.common.BrokerOperationException(
                    "Failed to fetch quotes: " + ex.getMessage(), ex);
        }
    }

    public static OrderParams toOrderParams(BrokerOrderRequest req, KiteUser user) {
        OrderParams p = new OrderParams();
        p.tradingsymbol = req.tradingsymbol();
        p.exchange = firstNonBlank(req.exchange(), user.getDefaultExchange(), Constants.EXCHANGE_NSE);
        p.transactionType = req.transactionType() == null
                ? Constants.TRANSACTION_TYPE_BUY
                : req.transactionType().toUpperCase();
        p.orderType = firstNonBlank(req.orderType(), user.getDefaultOrderType(), Constants.ORDER_TYPE_MARKET);
        p.product = firstNonBlank(req.product(), user.getDefaultProduct(), Constants.PRODUCT_CNC);
        p.validity = firstNonBlank(req.validity(), Constants.VALIDITY_DAY);
        p.quantity = req.quantity() != null ? req.quantity() : user.getDefaultQuantity();
        if (req.price() != null) p.price = req.price();
        if (req.triggerPrice() != null) p.triggerPrice = req.triggerPrice();
        if (req.disclosedQuantity() != null) p.disclosedQuantity = req.disclosedQuantity();
        if (req.tag() != null && !req.tag().isBlank()) p.tag = req.tag();
        return p;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
