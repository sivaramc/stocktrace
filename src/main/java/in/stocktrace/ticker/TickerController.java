package in.stocktrace.ticker;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/ticker")
public class TickerController {

    private final TickerService service;
    private final TickLogRepository logRepo;

    public TickerController(TickerService service, TickLogRepository logRepo) {
        this.service = service;
        this.logRepo = logRepo;
    }

    @PostMapping("/{userId}/connect")
    public Map<String, Object> connect(@PathVariable String userId) {
        service.connect(userId);
        return Map.of("userId", userId, "connected", service.isConnected(userId));
    }

    @PostMapping("/{userId}/disconnect")
    public Map<String, Object> disconnect(@PathVariable String userId) {
        service.disconnect(userId);
        return Map.of("userId", userId, "connected", service.isConnected(userId));
    }

    @PostMapping("/{userId}/subscribe")
    public Map<String, Object> subscribe(@PathVariable String userId, @RequestBody TokenList body) {
        service.subscribe(userId, Set.copyOf(body.instrumentTokens()));
        return Map.of("userId", userId, "subscribed", service.subscribedTokens(userId));
    }

    @PostMapping("/{userId}/unsubscribe")
    public Map<String, Object> unsubscribe(@PathVariable String userId, @RequestBody TokenList body) {
        service.unsubscribe(userId, Set.copyOf(body.instrumentTokens()));
        return Map.of("userId", userId, "subscribed", service.subscribedTokens(userId));
    }

    @GetMapping("/{userId}/status")
    public Map<String, Object> status(@PathVariable String userId) {
        return Map.of(
                "userId", userId,
                "connected", service.isConnected(userId),
                "subscribed", service.subscribedTokens(userId)
        );
    }

    @GetMapping("/ticks")
    public Page<TickLog> ticks(@PageableDefault(size = 100) Pageable pageable) {
        return logRepo.findAllByOrderByReceivedAtDesc(pageable);
    }

    public record TokenList(List<Long> instrumentTokens) {}
}
