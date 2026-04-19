package in.stocktrace.webhook.chartink;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.stocktrace.broker.OrderFanoutService;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import in.stocktrace.feed.FeedBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint for Chartink to POST scanner alerts to.
 * <p>
 * Configure in Chartink: set the webhook URL to
 * {@code https://your-host/api/webhook/chartink?secret=...&exchange=NSE&quantity=1} (optional query params).
 * For each stock in the alert payload we place a BUY order on every user where
 * {@code active = true}.
 */
@RestController
@RequestMapping("/api/webhook/chartink")
public class ChartinkWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ChartinkWebhookController.class);

    private final OrderFanoutService fanout;
    private final WebhookEventRepository events;
    private final ObjectMapper mapper;
    private final FeedBroadcaster feed;
    private final String configuredSecret;

    public ChartinkWebhookController(OrderFanoutService fanout,
                                     WebhookEventRepository events,
                                     ObjectMapper mapper,
                                     FeedBroadcaster feed,
                                     @Value("${stocktrace.webhook.chartink.secret:}") String configuredSecret) {
        this.fanout = fanout;
        this.events = events;
        this.mapper = mapper;
        this.feed = feed;
        this.configuredSecret = configuredSecret;
    }

    @PostMapping
    public ResponseEntity<ChartinkResponse> onAlert(
            @RequestBody ChartinkPayload payload,
            @RequestParam(required = false) String secret,
            @RequestParam(required = false) String exchange,
            @RequestParam(required = false) String product,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) Integer quantity,
            @RequestParam(required = false, defaultValue = "BUY") String transactionType,
            @RequestParam(required = false, defaultValue = "true") boolean useTriggerPriceAsLimit
    ) {
        if (configuredSecret != null && !configuredSecret.isBlank() && !constantTimeEquals(configuredSecret, secret)) {
            log.warn("Chartink webhook rejected: bad secret");
            return ResponseEntity.status(401).build();
        }

        persistEvent(payload);

        String[] stocks = payload.stockList();
        String[] prices = payload.triggerPriceList();
        List<ChartinkStockResult> byStock = new ArrayList<>();

        for (int i = 0; i < stocks.length; i++) {
            String symbol = stocks[i];
            Double price = null;
            if (useTriggerPriceAsLimit && i < prices.length) {
                try {
                    price = Double.parseDouble(prices[i]);
                } catch (NumberFormatException ignored) {
                    // price stays null -> market order
                }
            }

            // When we have a trigger price and the caller did not explicitly pick an
            // orderType, upgrade to LIMIT so the price is actually honoured by Kite
            // (MARKET orders ignore the price field).
            String resolvedOrderType = orderType;
            if (price != null && (resolvedOrderType == null || resolvedOrderType.isBlank())) {
                resolvedOrderType = "LIMIT";
            }

            BrokerOrderRequest req = new BrokerOrderRequest(
                    symbol,
                    exchange,
                    transactionType,
                    resolvedOrderType,   // null -> per-user default (usually MARKET)
                    product,
                    null,                // variety -> per-user default (regular)
                    null,
                    quantity,
                    price,
                    null,
                    null,
                    "chartink"
            );

            List<BrokerOrderResult> results = fanout.placeForAllActive("CHARTINK:" + payload.scan_url(), req);
            byStock.add(new ChartinkStockResult(symbol, price, results));

            // Push a BUY/SELL tile for the UI stocks page to render.
            Map<String, Object> tile = new LinkedHashMap<>();
            tile.put("tradingsymbol", symbol);
            tile.put("exchange", exchange);
            tile.put("transactionType", transactionType);
            tile.put("triggerPrice", price);
            tile.put("scanName", payload.scan_name());
            tile.put("alertName", payload.alert_name());
            tile.put("scanUrl", payload.scan_url());
            tile.put("receivedAt", Instant.now().toString());
            feed.broadcast("chartink", tile);
        }

        return ResponseEntity.ok(new ChartinkResponse(
                payload.scan_name(),
                payload.alert_name(),
                payload.triggered_at(),
                byStock
        ));
    }

    @GetMapping("/events")
    public Page<WebhookEvent> listEvents(@PageableDefault(size = 50) Pageable pageable) {
        return events.findAllByOrderByReceivedAtDesc(pageable);
    }

    /** Constant-time comparison to avoid leaking the webhook secret via response timing. */
    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = (provided == null ? "" : provided).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    private void persistEvent(ChartinkPayload payload) {
        try {
            WebhookEvent e = new WebhookEvent();
            e.setSource("CHARTINK");
            e.setPayload(mapper.writeValueAsString(payload));
            e.setScanName(payload.scan_name());
            e.setAlertName(payload.alert_name());
            e.setStocks(payload.stocks());
            e.setTriggerPrices(payload.trigger_prices());
            e.setTriggeredAt(payload.triggered_at());
            events.save(e);
        } catch (Exception ex) {
            // Best-effort persistence: a serialization or DB failure here must not
            // block the downstream order fan-out.
            log.warn("Unable to persist Chartink webhook payload: {}", ex.getMessage());
        }
    }

    public record ChartinkResponse(
            String scanName,
            String alertName,
            String triggeredAt,
            List<ChartinkStockResult> stocks
    ) {}

    public record ChartinkStockResult(
            String tradingsymbol,
            Double triggerPrice,
            List<BrokerOrderResult> placements
    ) {}
}
