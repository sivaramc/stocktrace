package in.stocktrace.webhook.chartink;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "webhook_event")
@Getter
@Setter
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "scan_name", length = 256)
    private String scanName;

    @Column(name = "alert_name", length = 256)
    private String alertName;

    @Column(length = 1024)
    private String stocks;

    @Column(name = "trigger_prices", length = 1024)
    private String triggerPrices;

    @Column(name = "triggered_at", length = 64)
    private String triggeredAt;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
