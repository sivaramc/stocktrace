package in.stocktrace.broker.fivepaisa;

import com.FivePaisa.api.RestClient;
import in.stocktrace.common.BrokerOperationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 5paisa authentication endpoint. The TOTP exchange yields a JWT that the
 * underlying SDK keeps in memory; we also persist it on the user record so it
 * survives a process restart and can be inspected.
 */
@RestController
@RequestMapping("/api/5paisa/auth")
public class FivePaisaAuthController {

    private final FivePaisaUserService userService;
    private final FivePaisaClientFactory factory;

    public FivePaisaAuthController(FivePaisaUserService userService, FivePaisaClientFactory factory) {
        this.userService = userService;
        this.factory = factory;
    }

    @PostMapping("/{userId}/totp-session")
    public Map<String, Object> totpSession(@PathVariable String userId,
                                           @Valid @RequestBody TotpSessionRequest body) {
        FivePaisaUser user = userService.getRequired(userId);
        RestClient client = factory.forUser(userId);
        try {
            String jwt = client.getTotpSession(user.getClientCode(), body.totp(), body.pin());
            // 5paisa JWTs are valid for the trading day; expire conservatively in 12h.
            Instant expires = Instant.now().plus(Duration.ofHours(12));
            userService.saveJwt(userId, jwt, expires);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("userId", userId);
            result.put("jwt", jwt);
            result.put("expiresAt", expires.toString());
            return result;
        } catch (Exception ex) {
            throw new BrokerOperationException("5paisa TOTP session failed: " + ex.getMessage(), ex);
        }
    }

    public record TotpSessionRequest(@NotBlank String totp, @NotBlank String pin) {}
}
