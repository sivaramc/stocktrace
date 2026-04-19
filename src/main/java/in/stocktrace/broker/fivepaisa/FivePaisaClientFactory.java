package in.stocktrace.broker.fivepaisa;

import com.FivePaisa.api.RestClient;
import com.FivePaisa.config.AppConfig;
import com.FivePaisa.service.Properties;
import org.springframework.stereotype.Component;

/**
 * Builds {@link RestClient} instances scoped to a single {@link FivePaisaUser}.
 *
 * <p>{@code RestClient} carries mutable state (cookies + JWT) inside its
 * {@code AppConfig} after a call to {@code getTotpSession}. We therefore cache
 * one {@code RestClient} per stocktrace user-id and replay the JWT exchange on
 * demand from the caller (typically the auth controller).
 */
@Component
public class FivePaisaClientFactory {

    private final FivePaisaUserService userService;
    private final java.util.Map<String, RestClient> clients = new java.util.concurrent.ConcurrentHashMap<>();

    public FivePaisaClientFactory(FivePaisaUserService userService) {
        this.userService = userService;
    }

    /** Returns (creating if needed) the cached {@link RestClient} for the given user. */
    public RestClient forUser(String userId) {
        return clients.computeIfAbsent(userId, k -> build(userService.getRequired(k)));
    }

    /** Returns the persisted {@link FivePaisaUser} alongside its {@link RestClient}. */
    public FivePaisaUser userFor(String userId) {
        return userService.getRequired(userId);
    }

    /** Drop the cached client (e.g. on credential rotation). */
    public void evict(String userId) {
        clients.remove(userId);
    }

    private RestClient build(FivePaisaUser user) {
        AppConfig config = new AppConfig();
        config.setAppName(user.getAppName());
        config.setAppVer(user.getAppVer());
        config.setOsName(user.getOsName());
        config.setEncryptKey(user.getEncryptKey());
        config.setKey(user.getUserKey());
        config.setUserId(user.getFivepaisaUserId());
        config.setPassword(user.getPassword());
        config.setLoginId(user.getLoginId());

        Properties properties = new Properties();
        properties.setClientcode(user.getClientCode());

        return new RestClient(config, properties);
    }
}
