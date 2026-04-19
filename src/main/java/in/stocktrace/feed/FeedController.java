package in.stocktrace.feed;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Authenticated SSE stream of stock/feed events. The UI subscribes on login and
 * receives {@code chartink} events as they arrive at {@code /api/webhook/chartink}.
 *
 * <p>Clients should auto-reconnect on network drops; the default 6-hour server
 * timeout is there to force renewals and reap abandoned connections.
 */
@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedBroadcaster broadcaster;

    public FeedController(FeedBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(value = "/stocks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stocks() {
        return broadcaster.subscribe();
    }
}
