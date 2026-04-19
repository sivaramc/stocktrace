package in.stocktrace.broker.fivepaisa;

import com.FivePaisa.api.RestClient;
import in.stocktrace.broker.BrokerService;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import in.stocktrace.broker.model.BrokerQuote;
import in.stocktrace.common.BrokerOperationException;
import okhttp3.Response;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 5paisa implementation of {@link BrokerService}.
 *
 * <p><strong>Symbol convention:</strong> 5paisa identifies instruments by
 * numeric {@code ScripCode}, not by trading symbol. For 5paisa fan-outs the
 * caller must populate {@link BrokerOrderRequest#tradingsymbol()} with the
 * scrip code as a string (e.g. {@code "1594"}). The exchange field accepts
 * the 5paisa one-letter exchange codes ({@code N}, {@code B}, {@code M}); if
 * an NSE/BSE/MCX value is passed it is mapped automatically.
 */
@Service
public class FivePaisaBrokerService implements BrokerService {

    private static final Logger log = LoggerFactory.getLogger(FivePaisaBrokerService.class);

    private final FivePaisaClientFactory factory;
    private final FivePaisaUserService userService;

    public FivePaisaBrokerService(FivePaisaClientFactory factory, FivePaisaUserService userService) {
        this.factory = factory;
        this.userService = userService;
    }

    @Override
    public String id() {
        return "5paisa";
    }

    @Override
    @SuppressWarnings("unchecked")
    public BrokerOrderResult placeOrder(String brokerUserId, BrokerOrderRequest request) {
        // Mirror the ZerodhaBrokerService contract: never throw from placeOrder,
        // always return a BrokerOrderResult so callers can rely on uniform handling.
        try {
            FivePaisaUser user = userService.getRequired(brokerUserId);

            int scripCode = parseScripCode(request.tradingsymbol());
            Integer qty = request.quantity() != null ? request.quantity() : user.getDefaultQuantity();
            Double price = request.price();
            boolean atMarket = price == null || price <= 0d;
            String exchange = mapExchange(request.exchange() != null ? request.exchange() : user.getDefaultExchange());
            String exchangeType = user.getDefaultExchangeType();

            JSONObject body = new JSONObject();
            body.put("ClientCode", user.getClientCode());
            body.put("OrderFor", "P");
            body.put("Exchange", exchange);
            body.put("ExchangeType", exchangeType);
            body.put("ScripCode", scripCode);
            body.put("Price", atMarket ? 0d : price);
            body.put("OrderID", 0);
            body.put("OrderType", request.transactionType() != null ? request.transactionType() : "BUY");
            body.put("Qty", qty);
            body.put("OrderDateTime", "/Date(" + System.currentTimeMillis() + ")/");
            body.put("AtMarket", atMarket);
            body.put("RemoteOrderID", "stocktrace-" + System.currentTimeMillis());
            body.put("ExchOrderID", 0);
            body.put("DisQty", 0);
            body.put("IsStopLossOrder", false);
            body.put("StopLossPrice", request.triggerPrice() != null ? request.triggerPrice() : 0d);
            body.put("IsVTD", false);
            body.put("IOCOrder", false);
            body.put("IsIntraday", user.isDefaultIsIntraday());
            body.put("AHPlaced", "N");
            body.put("iOrderValidity", 0);
            body.put("OrderRequesterCode", user.getClientCode());
            body.put("TradedQty", 0);
            body.put("AppSource", 11033);

            // The cached RestClient is not thread-safe, so go through the factory's
            // per-user synchronised execute() helper. Different users still run in
            // parallel (different monitors); only same-user calls serialise.
            return factory.execute(brokerUserId, client -> {
                try (Response response = client.placeOrderRequest(body)) {
                    String raw = response.body() != null ? response.body().string() : "";
                    // json-simple's JSONParser is stateful and NOT thread-safe; use a fresh
                    // parser per parse() call.
                    JSONObject parsed = (JSONObject) new JSONParser().parse(raw);
                    JSONObject inner = parsed != null ? (JSONObject) parsed.get("body") : null;
                    String brokerOrderId = inner != null && inner.get("BrokerOrderID") != null
                            ? String.valueOf(inner.get("BrokerOrderID")) : null;
                    String message = inner != null && inner.get("Message") != null
                            ? String.valueOf(inner.get("Message")) : raw;
                    int status = inner != null && inner.get("Status") != null
                            ? ((Number) inner.get("Status")).intValue() : -1;
                    if (status == 0 || brokerOrderId != null) {
                        return BrokerOrderResult.ok(brokerUserId, brokerOrderId);
                    }
                    return BrokerOrderResult.fail(brokerUserId, message);
                }
            });
        } catch (Exception ex) {
            log.warn("5paisa placeOrder failed for {}: {}", brokerUserId, ex.getMessage());
            return BrokerOrderResult.fail(brokerUserId, ex.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, BrokerQuote> getQuotes(String brokerUserId, List<String> instruments) {
        FivePaisaUser user = userService.getRequired(brokerUserId);

        JSONArray marketFeedData = new JSONArray();
        for (String key : instruments) {
            String[] parts = key.split(":");
            if (parts.length != 2) {
                throw new BrokerOperationException("5paisa getQuotes expects EXCHANGE:SCRIPCODE entries, got: " + key);
            }
            String exchange = mapExchange(parts[0]);
            int scrip = parseScripCode(parts[1]);
            JSONObject entry = new JSONObject();
            entry.put("Exch", exchange);
            entry.put("ExchType", user.getDefaultExchangeType());
            entry.put("ScripCode", scrip);
            marketFeedData.add(entry);
        }

        JSONObject body = new JSONObject();
        body.put("ClientLoginType", 0);
        body.put("ClientCode", user.getClientCode());
        body.put("LastRequestTime", "/Date(" + System.currentTimeMillis() + ")/");
        body.put("RefreshRate", "H");
        body.put("MarketFeedData", marketFeedData);

        try {
            return factory.execute(brokerUserId, client -> {
                try (Response response = client.marketFeed(body)) {
                    String raw = response.body() != null ? response.body().string() : "";
                    JSONObject parsed = (JSONObject) new JSONParser().parse(raw);
                    JSONObject inner = parsed != null ? (JSONObject) parsed.get("body") : null;
                    JSONArray rows = inner != null ? (JSONArray) inner.get("Data") : null;
                    Map<String, BrokerQuote> out = new LinkedHashMap<>();
                    if (rows == null) {
                        return out;
                    }
                    for (Object o : rows) {
                        JSONObject row = (JSONObject) o;
                        String exch = String.valueOf(row.get("Exch"));
                        Object scripObj = row.get("Token");
                        Object ltpObj = row.get("LastRate");
                        if (scripObj == null || ltpObj == null) {
                            continue;
                        }
                        String key = exch + ":" + scripObj;
                        Long token = scripObj instanceof Number n ? n.longValue() : null;
                        Double ltp = ((Number) ltpObj).doubleValue();
                        Double open = numberOrNull(row.get("Open"));
                        Double high = numberOrNull(row.get("High"));
                        Double low = numberOrNull(row.get("Low"));
                        Double close = numberOrNull(row.get("PClose"));
                        Long volume = row.get("TotalQty") instanceof Number nv ? nv.longValue() : null;
                        out.put(key, new BrokerQuote(key, token, ltp, open, high, low, close, volume));
                    }
                    return out;
                }
            });
        } catch (Exception ex) {
            log.warn("5paisa marketFeed failed for {}: {}", brokerUserId, ex.getMessage());
            throw new BrokerOperationException("5paisa marketFeed failed: " + ex.getMessage(), ex);
        }
    }

    private static Double numberOrNull(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    /** Translate human exchange names to 5paisa one-letter codes. */
    static String mapExchange(String exchange) {
        if (exchange == null) return "N";
        return switch (exchange.toUpperCase()) {
            case "NSE", "N" -> "N";
            case "BSE", "B" -> "B";
            case "MCX", "M" -> "M";
            default -> exchange; // pass through if caller already supplied a code we don't know
        };
    }

    static int parseScripCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BrokerOperationException("5paisa requires a numeric ScripCode in BrokerOrderRequest.tradingsymbol");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BrokerOperationException("5paisa requires a numeric ScripCode, got: " + value);
        }
    }

    /**
     * @return {@code true} iff {@code value} is a non-blank string that parses
     *         to a positive integer (i.e. a valid 5paisa ScripCode). Used by
     *         the fan-out path to skip 5paisa users when the rule / webhook
     *         carries a Kite-style trading symbol rather than a ScripCode.
     */
    public static boolean isValidScripCode(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            return Integer.parseInt(value.trim()) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
