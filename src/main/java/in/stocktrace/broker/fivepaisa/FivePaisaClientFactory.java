package in.stocktrace.broker.fivepaisa;

import com.FivePaisa.api.RestClient;
import com.FivePaisa.config.AppConfig;
import com.FivePaisa.service.Properties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and hands out {@link RestClient} instances scoped to a single
 * {@link FivePaisaUser}.
 *
 * <p><strong>Thread-safety:</strong> the 5paisa SDK's {@code RestClient}
 * carries mutable state that is <em>not</em> thread-safe &mdash; it holds
 * internal {@code org.json.simple.parser.JSONParser} instances and mutable
 * cookie/JWT fields inside {@code ApiCalls}. The cached client per user must
 * therefore be accessed under a lock so concurrent callers (fan-out threads,
 * parallel passthrough HTTP requests, or a TOTP re-auth running alongside a
 * placeOrder) never race on that state.
 *
 * <p>We also cannot simply build a fresh client per call: the JWT obtained
 * from {@code getTotpSession} is stored on the {@code RestClient} and has no
 * public setter, so a new client wouldn't be authenticated.
 *
 * <p>Callers should go through {@link #execute(String, ClientCall)} rather
 * than using {@link #forUser(String)} directly. {@link #forUser(String)} is
 * kept for read-only inspection (e.g. tests) but is not safe for concurrent
 * use.
 */
@Component
public class FivePaisaClientFactory {

    private final FivePaisaUserService userService;
    private final Map<String, RestClient> clients = new ConcurrentHashMap<>();

    public FivePaisaClientFactory(FivePaisaUserService userService) {
        this.userService = userService;
    }

    /**
     * Returns (creating if needed) the cached {@link RestClient} for the given
     * user. The returned instance is <em>not</em> thread-safe; callers that
     * invoke methods on it must do so under {@code synchronized(client)} or,
     * preferably, use {@link #execute(String, ClientCall)}.
     */
    public RestClient forUser(String userId) {
        return clients.computeIfAbsent(userId, k -> build(userService.getRequired(k)));
    }

    /**
     * Runs {@code call} against the cached {@link RestClient} for {@code userId}
     * while holding the monitor on that client, serialising same-user access so
     * the SDK's non-thread-safe mutable state (JSONParser, cookies, JWT) cannot
     * be corrupted by concurrent callers. Different users still run in parallel
     * because each has its own cached client and therefore its own monitor.
     */
    public <T> T execute(String userId, ClientCall<T> call) throws Exception {
        RestClient client = forUser(userId);
        synchronized (client) {
            return call.apply(client);
        }
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

    /** Function that invokes a single method on the 5paisa {@link RestClient}. */
    @FunctionalInterface
    public interface ClientCall<T> {
        T apply(RestClient client) throws Exception;
    }
}
