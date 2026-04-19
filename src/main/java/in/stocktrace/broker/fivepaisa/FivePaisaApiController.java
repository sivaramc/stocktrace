package in.stocktrace.broker.fivepaisa;

import com.FivePaisa.api.RestClient;
import in.stocktrace.common.BrokerOperationException;
import okhttp3.Response;
import org.json.simple.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Thin pass-through wrappers exposing every method on the 5paisa SDK
 * {@link RestClient}. The request body is forwarded as JSON to the SDK and
 * the SDK response body is returned verbatim, preserving the upstream schema.
 *
 * <p>The stocktrace user id is taken from the {@code X-User-Id} request header.
 */
@RestController
@RequestMapping("/api/5paisa")
public class FivePaisaApiController {

    private final FivePaisaClientFactory factory;

    public FivePaisaApiController(FivePaisaClientFactory factory) {
        this.factory = factory;
    }

    // --------------------------------------------------------------------- orders

    @PostMapping(value = "/orders/place", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> placeOrder(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::placeOrderRequest);
    }

    @PostMapping(value = "/orders/modify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> modifyOrder(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::modifyOrderRequest);
    }

    @PostMapping(value = "/orders/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> cancelOrder(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::cancelOrderRequest);
    }

    @PostMapping(value = "/orders/smo/place", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> placeSmo(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::smoOrderRequest);
    }

    @PostMapping(value = "/orders/smo/modify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> modifySmo(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::modifySmoOrder);
    }

    @PostMapping(value = "/orders/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> orderStatus(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::orderStatus);
    }

    @PostMapping(value = "/orders/book", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> orderBook(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::orderBookV2);
    }

    @PostMapping(value = "/orders/trades", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> tradeInfo(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::tradeInformation);
    }

    // ------------------------------------------------------------------ portfolio

    @PostMapping(value = "/portfolio/holdings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> holdings(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::holdingV2);
    }

    @PostMapping(value = "/portfolio/positions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> positions(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::netPositionNetWiseV1);
    }

    @PostMapping(value = "/portfolio/margin", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> margin(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::marginV3);
    }

    // ----------------------------------------------------------------- marketdata

    @PostMapping(value = "/marketdata/feed", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> marketFeed(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        return invoke(userId, body, RestClient::marketFeed);
    }

    // --------------------------------------------------------------------- login

    @PostMapping(value = "/auth/login-check", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> loginCheck(@RequestHeader("X-User-Id") String userId, @RequestBody Map<String, Object> body) {
        JSONObject payload = toJson(body);
        try {
            String resp = factory.execute(userId, client -> client.loginCheck(payload));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resp);
        } catch (Exception ex) {
            throw new BrokerOperationException("5paisa loginCheck failed: " + ex.getMessage(), ex);
        }
    }

    // ------------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface RestCall {
        Response call(RestClient client, JSONObject body) throws Exception;
    }

    private ResponseEntity<String> invoke(String userId, Map<String, Object> body, RestCall fn) {
        JSONObject payload = toJson(body);
        try {
            // Serialise on the cached RestClient for this user; the SDK is not
            // thread-safe and concurrent callers would otherwise corrupt shared
            // JSONParser / cookie / JWT state.
            return factory.execute(userId, client -> {
                try (Response response = fn.call(client, payload)) {
                    String raw = response.body() != null ? response.body().string() : "";
                    return ResponseEntity.status(response.code())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(raw);
                }
            });
        } catch (Exception ex) {
            throw new BrokerOperationException("5paisa upstream call failed: " + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static JSONObject toJson(Map<String, Object> body) {
        JSONObject json = new JSONObject();
        if (body != null) {
            json.putAll(body);
        }
        return json;
    }
}
