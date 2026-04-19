package in.stocktrace.scheduler;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "scan_rule")
@Getter
@Setter
public class ScanRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 16)
    private String exchange = "NSE";

    @Column(nullable = false, length = 64)
    private String tradingsymbol;

    @Column(name = "instrument_token")
    private Long instrumentToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 32)
    private ConditionType conditionType;

    @Column(name = "threshold_low")
    private Double thresholdLow;

    @Column(name = "threshold_high")
    private Double thresholdHigh;

    @Column(name = "transaction_type", nullable = false, length = 8)
    private String transactionType = "BUY";

    @Column(nullable = false)
    private int quantity = 1;

    @Column(nullable = false, length = 16)
    private String product = "CNC";

    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType = "MARKET";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public enum ConditionType {
        LTP_ABOVE,
        LTP_BELOW,
        LTP_BETWEEN
    }

    public String instrumentKey() {
        return exchange + ":" + tradingsymbol;
    }

    public boolean matches(double ltp) {
        return switch (conditionType) {
            case LTP_ABOVE -> thresholdHigh != null && ltp > thresholdHigh;
            case LTP_BELOW -> thresholdLow != null && ltp < thresholdLow;
            case LTP_BETWEEN -> thresholdLow != null && thresholdHigh != null
                    && ltp >= thresholdLow && ltp <= thresholdHigh;
        };
    }
}
