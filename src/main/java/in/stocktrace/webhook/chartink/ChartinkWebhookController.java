package in.stocktrace.webhook.chartink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.stocktrace.broker.OrderFanoutService;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

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
    private final String configuredSecret;

    public ChartinkWebhookController(OrderFanoutService fanout,
                                     WebhookEventRepository events,
                                     ObjectMapper mapper,
                                     @Value("${stocktrace.webhook.chartink.secret:}") String configuredSecret) {
        this.fanout = fanout;
        this.events = events;
        this.mapper = mapper;
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
        if (configuredSecret != null && !configuredSecret.isBlank() && !configuredSecret.equals(secret)) {
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

            BrokerOrderRequest req = new BrokerOrderRequest(
                    symbol,
                    exchange,
                    transactionType,
                    orderType,           // null -> per-user default (usually MARKET)
                    product,
                    null,                // variety -> per-user default (regular)
                    null,
                    quantity,
                    price,               // if MARKET order (null orderType), price is ignored by Kite
                    null,
                    null,
                    "chartink"
            );

            List<BrokerOrderResult> results = fanout.placeForAllActive("CHARTINK:" + payload.scan_url(), req);
            byStock.add(new ChartinkStockResult(symbol, price, results));
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
        } catch (JsonProcessingException ex) {
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
