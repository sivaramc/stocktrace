package in.stocktrace.broker;

import in.stocktrace.common.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class BrokerRegistry {

    private final Map<String, BrokerService> brokers;
    private final String defaultBroker;

    public BrokerRegistry(List<BrokerService> brokers,
                          @Value("${stocktrace.broker.default:zerodha}") String defaultBroker) {
        this.brokers = brokers.stream().collect(Collectors.toMap(BrokerService::id, Function.identity()));
        this.defaultBroker = defaultBroker;
    }

    public BrokerService get(String brokerId) {
        BrokerService b = brokers.get(brokerId);
        if (b == null) {
            throw new NotFoundException("Unknown broker: " + brokerId);
        }
        return b;
    }

    public boolean isRegistered(String brokerId) {
        return brokers.containsKey(brokerId);
    }

    public BrokerService getDefault() {
        return get(defaultBroker);
    }

    public List<String> availableBrokers() {
        return brokers.keySet().stream().sorted().toList();
    }
}
