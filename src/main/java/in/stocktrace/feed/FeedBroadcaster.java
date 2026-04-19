package in.stocktrace.feed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process fan-out of stock/feed events to connected SSE clients. Each
 * subscriber gets its own {@link SseEmitter}; broadcast attempts best-effort
 * delivery and prunes dead emitters on failure.
 */
@Component
public class FeedBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(FeedBroadcaster.class);
    /** Long-lived — browsers keep SSE open. */
    private static final long DEFAULT_TIMEOUT_MS = 6L * 60L * 60L * 1000L; // 6h

    private final Queue<SseEmitter> emitters = new ConcurrentLinkedQueue<>();
    private final AtomicLong eventSeq = new AtomicLong();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(t -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("{}"));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    /** Broadcast a named event with a JSON-serialisable payload. */
    public void broadcast(String name, Object payload) {
        if (emitters.isEmpty()) return;
        long id = eventSeq.incrementAndGet();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().id(String.valueOf(id)).name(name).data(payload));
            } catch (Exception ex) {
                log.debug("SSE send failed, dropping emitter: {}", ex.toString());
                emitters.remove(emitter);
                try { emitter.completeWithError(ex); } catch (Exception ignored) {}
            }
        }
    }

    public int subscriberCount() {
        return emitters.size();
    }
}
