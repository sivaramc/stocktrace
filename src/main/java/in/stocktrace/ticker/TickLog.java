package in.stocktrace.ticker;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tick_log")
@Getter
@Setter
public class TickLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kite_user_id", nullable = false, length = 64)
    private String kiteUserId;

    @Column(name = "instrument_token", nullable = false)
    private long instrumentToken;

    @Column(name = "last_price")
    private Double lastPrice;

    @Column(name = "volume_traded_today")
    private Long volumeTradedToday;

    @Column(name = "exchange_timestamp")
    private Instant exchangeTimestamp;

    @Column(name = "tick_timestamp")
    private Instant tickTimestamp;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();
}
