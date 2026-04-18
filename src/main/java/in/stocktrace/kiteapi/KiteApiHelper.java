package in.stocktrace.kiteapi;

import com.zerodhatech.kiteconnect.KiteConnect;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import in.stocktrace.broker.zerodha.KiteConnectFactory;
import in.stocktrace.common.BrokerOperationException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Thin helper that executes Kite SDK calls for a specific user, wrapping checked
 * exceptions into a single {@link BrokerOperationException}.
 */
@Component
public class KiteApiHelper {

    private final KiteConnectFactory factory;

    public KiteApiHelper(KiteConnectFactory factory) {
        this.factory = factory;
    }

    public <T> T call(String userId, KiteCall<T> call) {
        KiteConnect kite = factory.forUser(userId);
        try {
            return call.execute(kite);
        } catch (KiteException | IOException | org.json.JSONException ex) {
            throw new BrokerOperationException(
                    "Kite API call failed for user " + userId + ": " + ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    public interface KiteCall<T> {
        T execute(KiteConnect kite) throws KiteException, IOException, org.json.JSONException;
    }
}
