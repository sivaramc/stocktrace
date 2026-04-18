package in.stocktrace.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "kite_users")
@Getter
@Setter
public class KiteUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(length = 128)
    private String label;

    @Column(name = "api_key", nullable = false, length = 128)
    private String apiKey;

    @Column(name = "api_secret", nullable = false, length = 256)
    private String apiSecret;

    @Column(name = "access_token", length = 256)
    private String accessToken;

    @Column(name = "public_token", length = 256)
    private String publicToken;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "default_exchange", nullable = false, length = 16)
    private String defaultExchange = "NSE";

    @Column(name = "default_product", nullable = false, length = 16)
    private String defaultProduct = "CNC";

    @Column(name = "default_order_type", nullable = false, length = 16)
    private String defaultOrderType = "MARKET";

    @Column(name = "default_quantity", nullable = false)
    private int defaultQuantity = 1;

    @Column(name = "default_variety", nullable = false, length = 16)
    private String defaultVariety = "regular";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
