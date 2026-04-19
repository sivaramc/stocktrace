package in.stocktrace.broker;

import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import in.stocktrace.broker.model.BrokerQuote;

import java.util.List;
import java.util.Map;

/**
 * Cross-broker abstraction. Implementations exist per broker (Zerodha Kite, 5paisa, ...).
 * This covers the minimum surface needed by generic flows (Chartink webhook fan-out,
 * scheduled scanner). Broker-native extras (GTT, MF, historical, etc.) are exposed by
 * the broker-specific services directly.
 */
public interface BrokerService {

    /** Unique id for this broker, e.g. "zerodha", "5paisa". */
    String id();

    /** Place an order on behalf of the given broker user. */
    BrokerOrderResult placeOrder(String brokerUserId, BrokerOrderRequest request);

    /** Fetch quotes keyed by "EXCHANGE:TRADINGSYMBOL". */
    Map<String, BrokerQuote> getQuotes(String brokerUserId, List<String> instruments);
}
