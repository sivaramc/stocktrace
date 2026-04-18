package in.stocktrace.broker.zerodha;

import com.zerodhatech.kiteconnect.KiteConnect;
import in.stocktrace.common.BrokerOperationException;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserService;
import org.springframework.stereotype.Component;

/**
 * Creates {@link KiteConnect} instances on demand for a given stored user.
 * We intentionally do NOT cache these across requests because access tokens
 * expire daily and can be rotated at any time from the admin console.
 */
@Component
public class KiteConnectFactory {

    private final KiteUserService userService;

    public KiteConnectFactory(KiteUserService userService) {
        this.userService = userService;
    }

    /** Kite client with just the API key set (used for login URL / session generation). */
    public KiteConnect forLogin(String brokerUserId) {
        KiteUser u = userService.getRequired(brokerUserId);
        KiteConnect kite = new KiteConnect(u.getApiKey());
        kite.setUserId(u.getUserId());
        return kite;
    }

    /** Kite client with API key + access token set (used for authenticated calls). */
    public KiteConnect forUser(String brokerUserId) {
        KiteUser u = userService.getRequired(brokerUserId);
        if (u.getAccessToken() == null || u.getAccessToken().isBlank()) {
            throw new BrokerOperationException("No access token for user " + brokerUserId
                    + ". Complete the /api/auth/" + brokerUserId + "/session flow first.");
        }
        KiteConnect kite = new KiteConnect(u.getApiKey());
        kite.setUserId(u.getUserId());
        kite.setAccessToken(u.getAccessToken());
        if (u.getPublicToken() != null) {
            kite.setPublicToken(u.getPublicToken());
        }
        return kite;
    }

    public KiteUser user(String brokerUserId) {
        return userService.getRequired(brokerUserId);
    }
}
