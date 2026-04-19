package in.stocktrace.app;

import com.FivePaisa.api.RestClient;
import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.models.User;
import in.stocktrace.app.security.AppUserPrincipal;
import in.stocktrace.broker.fivepaisa.FivePaisaClientFactory;
import in.stocktrace.broker.fivepaisa.FivePaisaUser;
import in.stocktrace.broker.fivepaisa.FivePaisaUserService;
import in.stocktrace.broker.zerodha.KiteConnectFactory;
import in.stocktrace.common.BrokerOperationException;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "TradeOn" — authenticated, owner-scoped wrapper for broker session exchange.
 *
 * <p>The webapp/mobile "TradeOn" button calls one of these endpoints to mint
 * the per-day broker session token that is then stored on the broker row.
 *
 * <ul>
 *   <li>5paisa: TOTP exchange (TOTP + PIN -&gt; JWT, ~12h validity)</li>
 *   <li>Zerodha: request_token exchange (from the Kite login redirect)</li>
 * </ul>
 *
 * Only the broker rows owned by the authenticated app user can be touched.
 */
@RestController
@RequestMapping("/api/trade-on")
public class TradeOnController {

    private final AppUserService appUsers;
    private final FivePaisaUserService fivePaisaUsers;
    private final FivePaisaClientFactory fivePaisaClient;
    private final KiteUserService kiteUsers;
    private final KiteConnectFactory kiteFactory;

    public TradeOnController(AppUserService appUsers,
                             FivePaisaUserService fivePaisaUsers,
                             FivePaisaClientFactory fivePaisaClient,
                             KiteUserService kiteUsers,
                             KiteConnectFactory kiteFactory) {
        this.appUsers = appUsers;
        this.fivePaisaUsers = fivePaisaUsers;
        this.fivePaisaClient = fivePaisaClient;
        this.kiteUsers = kiteUsers;
        this.kiteFactory = kiteFactory;
    }

    @PostMapping("/fivepaisa")
    public Map<String, Object> fivepaisa(@AuthenticationPrincipal AppUserPrincipal me,
                                         @Valid @RequestBody TotpRequest body) {
        FivePaisaUser user = appUsers.fivePaisaUserOwnedBy(me.getId())
                .orElseThrow(() -> new BrokerOperationException(
                        "No 5paisa account linked to this user. Add 5paisa details under your profile first."));
        try {
            String jwt = fivePaisaClient.execute(user.getUserId(),
                    (RestClient client) -> client.getTotpSession(user.getClientCode(), body.totp(), body.pin()));
            // 5paisa JWTs are valid for the trading day; expire conservatively in 12h.
            Instant expires = Instant.now().plus(Duration.ofHours(12));
            fivePaisaUsers.saveJwt(user.getUserId(), jwt, expires);
            // Flip active=true so the user participates in fan-out / self-trade immediately.
            // Re-fetch inside setActive(...) to avoid clobbering the JWT just persisted above.
            if (!user.isActive()) {
                fivePaisaUsers.setActive(user.getUserId(), true);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("broker", "5paisa");
            result.put("brokerUserId", user.getUserId());
            result.put("jwt", jwt);
            result.put("expiresAt", expires.toString());
            return result;
        } catch (Exception ex) {
            throw new BrokerOperationException("5paisa TOTP session failed: " + ex.getMessage(), ex);
        }
    }

    @GetMapping("/zerodha/login-url")
    public Map<String, String> zerodhaLoginUrl(@AuthenticationPrincipal AppUserPrincipal me) {
        KiteUser user = appUsers.kiteUserOwnedBy(me.getId())
                .orElseThrow(() -> new BrokerOperationException(
                        "No Zerodha account linked to this user. Add Zerodha details under your profile first."));
        KiteConnect kite = kiteFactory.forLogin(user.getUserId());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("broker", "zerodha");
        result.put("brokerUserId", user.getUserId());
        result.put("loginUrl", kite.getLoginURL());
        return result;
    }

    @PostMapping("/zerodha")
    public Map<String, Object> zerodha(@AuthenticationPrincipal AppUserPrincipal me,
                                       @Valid @RequestBody ZerodhaSessionRequest body) {
        KiteUser user = appUsers.kiteUserOwnedBy(me.getId())
                .orElseThrow(() -> new BrokerOperationException(
                        "No Zerodha account linked to this user. Add Zerodha details under your profile first."));
        User kiteUser;
        try {
            KiteConnect kite = kiteFactory.forLogin(user.getUserId());
            kiteUser = kite.generateSession(body.requestToken(), user.getApiSecret());
        } catch (Throwable ex) {
            // KiteException extends Throwable (not Exception) in the vendor SDK.
            throw new BrokerOperationException("Zerodha session exchange failed: " + ex.getMessage(), ex);
        }
        try {
            Instant expires = Instant.now().plus(Duration.ofHours(20));
            kiteUsers.saveSession(user.getUserId(), kiteUser.accessToken, kiteUser.publicToken, expires);
            if (!user.isActive()) {
                kiteUsers.setActive(user.getUserId(), true);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("broker", "zerodha");
            result.put("brokerUserId", user.getUserId());
            result.put("accessToken", kiteUser.accessToken);
            result.put("publicToken", kiteUser.publicToken);
            result.put("expiresAt", expires.toString());
            return result;
        } catch (RuntimeException ex) {
            throw new BrokerOperationException("Zerodha session persist failed: " + ex.getMessage(), ex);
        }
    }

    public record TotpRequest(@NotBlank String totp, @NotBlank String pin) {}

    public record ZerodhaSessionRequest(@NotBlank String requestToken) {}
}
