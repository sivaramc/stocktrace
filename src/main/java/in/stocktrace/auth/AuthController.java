package in.stocktrace.auth;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.TokenSet;
import com.zerodhatech.models.User;
import in.stocktrace.broker.zerodha.KiteConnectFactory;
import in.stocktrace.common.BrokerOperationException;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Per-user Kite Connect OAuth-style auth flow:
 *
 * 1. GET  /api/auth/{userId}/login-url
 *    returns the Kite login URL; open in browser, authorize, you'll be redirected
 *    to your app's redirect URL with ?request_token=...&action=login&status=success.
 *
 * 2. POST /api/auth/{userId}/session  { "requestToken": "..." }
 *    exchanges the request_token for an access_token and persists it for the user.
 *
 * 3. POST /api/auth/{userId}/renew    { "refreshToken": "..." }
 *    renews the access token using a refresh token (for long-lived Kite apps).
 *
 * 4. POST /api/auth/{userId}/logout   invalidates the current access token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final KiteConnectFactory factory;
    private final KiteUserService userService;

    public AuthController(KiteConnectFactory factory, KiteUserService userService) {
        this.factory = factory;
        this.userService = userService;
    }

    @GetMapping("/{userId}/login-url")
    public Map<String, String> loginUrl(@PathVariable String userId) {
        KiteConnect kite = factory.forLogin(userId);
        return Map.of("loginUrl", kite.getLoginURL());
    }

    @PostMapping("/{userId}/session")
    public Map<String, Object> session(@PathVariable String userId,
                                       @RequestBody SessionRequest body) {
        try {
            KiteUser dbUser = userService.getRequired(userId);
            KiteConnect kite = factory.forLogin(userId);
            User kiteUser = kite.generateSession(body.requestToken(), dbUser.getApiSecret());
            // Kite access tokens are valid until 6 AM the next day; store an approximate expiry (~24h).
            Instant expires = Instant.now().plus(Duration.ofHours(20));
            userService.saveSession(userId, kiteUser.accessToken, kiteUser.publicToken, expires);
            return Map.of(
                    "userId", userId,
                    "accessToken", kiteUser.accessToken,
                    "publicToken", kiteUser.publicToken,
                    "loginTime", kiteUser.loginTime == null ? null : kiteUser.loginTime.toString(),
                    "expiresAt", expires.toString()
            );
        } catch (KiteException | IOException | org.json.JSONException ex) {
            throw new BrokerOperationException("generateSession failed: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/{userId}/renew")
    public Map<String, Object> renew(@PathVariable String userId,
                                     @RequestBody RenewRequest body) {
        try {
            KiteUser dbUser = userService.getRequired(userId);
            KiteConnect kite = factory.forLogin(userId);
            TokenSet tokens = kite.renewAccessToken(body.refreshToken(), dbUser.getApiSecret());
            Instant expires = Instant.now().plus(Duration.ofHours(20));
            userService.saveSession(userId, tokens.accessToken, dbUser.getPublicToken(), expires);
            return Map.of(
                    "userId", userId,
                    "accessToken", tokens.accessToken,
                    "expiresAt", expires.toString()
            );
        } catch (KiteException | IOException | org.json.JSONException ex) {
            throw new BrokerOperationException("renewAccessToken failed: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/{userId}/logout")
    public Map<String, Object> logout(@PathVariable String userId) {
        try {
            KiteConnect kite = factory.forUser(userId);
            Object response = kite.logout();
            userService.saveSession(userId, null, null, null);
            return Map.of("userId", userId, "response", String.valueOf(response));
        } catch (KiteException | IOException | org.json.JSONException ex) {
            throw new BrokerOperationException("logout failed: " + ex.getMessage(), ex);
        }
    }

    public record SessionRequest(@NotBlank String requestToken) {}
    public record RenewRequest(@NotBlank String refreshToken) {}
}
