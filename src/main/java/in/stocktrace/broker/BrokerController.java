package in.stocktrace.broker;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/brokers")
public class BrokerController {

    private final BrokerRegistry registry;

    public BrokerController(BrokerRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<String, Object> list() {
        List<String> brokers = registry.availableBrokers();
        return Map.of(
                "brokers", brokers,
                "default", registry.getDefault().id()
        );
    }
}
