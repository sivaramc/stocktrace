package in.stocktrace.feed;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class FeedBroadcasterTest {

    @Test
    void subscribeRegistersEmitterAndBroadcastDoesNotThrow() {
        FeedBroadcaster b = new FeedBroadcaster();
        SseEmitter e1 = b.subscribe();
        SseEmitter e2 = b.subscribe();
        assertThat(b.subscriberCount()).isEqualTo(2);
        assertThat(e1).isNotNull();
        assertThat(e2).isNotNull();

        // No subscriber IO errors should bubble out of broadcast — the
        // ready event was already drained by subscribe().
        b.broadcast("chartink", java.util.Map.of("symbol", "INFY"));

        // And with no subscribers, broadcast is a no-op.
        FeedBroadcaster empty = new FeedBroadcaster();
        empty.broadcast("chartink", java.util.Map.of("symbol", "TCS"));
        assertThat(empty.subscriberCount()).isEqualTo(0);
    }
}
