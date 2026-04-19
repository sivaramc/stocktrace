package in.stocktrace.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "order_audit")
@Getter
@Setter
public class OrderAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kite_user_id", nullable = false, length = 64)
    private String kiteUserId;

    /** Broker that handled this order, e.g. {@code zerodha} or {@code 5paisa}. */
    @Column(nullable = false, length = 16)
    private String broker = "zerodha";

    @Column(nullable = false, length = 32)
    private String source;

    @Column(nullable = false, length = 64)
    private String tradingsymbol;

    @Column(nullable = false, length = 16)
    private String exchange;

    @Column(name = "transaction_type", nullable = false, length = 8)
    private String transactionType;

    @Column(name = "order_type", nullable = false, length = 16)
    private String orderType;

    @Column(nullable = false, length = 16)
    private String product;

    @Column(nullable = false)
    private int quantity;

    @Column
    private Double price;

    @Column(name = "trigger_price")
    private Double triggerPrice;

    @Column(name = "broker_order_id", length = 64)
    private String brokerOrderId;

    @Column(nullable = false, length = 16)
    private String status;   // SUCCESS / FAILED

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "request_json", columnDefinition = "TEXT")
    private String requestJson;

    @Column(name = "response_json", columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
