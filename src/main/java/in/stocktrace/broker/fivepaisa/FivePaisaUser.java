package in.stocktrace.broker.fivepaisa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Persistent record for a 5paisa account. Mirrors the fields the SDK's
 * {@code AppConfig} + {@code Properties} need to authenticate, plus the
 * JWT minted from the TOTP exchange.
 */
@Entity
@Table(name = "fivepaisa_users")
@Getter
@Setter
public class FivePaisaUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    /** FK to {@code app_users.id}; NULL for legacy / admin-managed rows. */
    @Column(name = "owner_app_user_id")
    private Long ownerAppUserId;

    @Column(length = 128)
    private String label;

    @Column(name = "app_name", nullable = false, length = 64)
    private String appName;

    @Column(name = "app_ver", nullable = false, length = 16)
    private String appVer = "1.0";

    @Column(name = "os_name", nullable = false, length = 16)
    private String osName = "WEB";

    @Column(name = "encrypt_key", nullable = false, length = 128)
    private String encryptKey;

    @Column(name = "user_key", nullable = false, length = 128)
    private String userKey;

    /** 5paisa-issued user id (distinct from our internal {@link #userId}). */
    @Column(name = "fivepaisa_user_id", nullable = false, length = 64)
    private String fivepaisaUserId;

    @Column(nullable = false, length = 128)
    private String password;

    @Column(name = "login_id", nullable = false, length = 64)
    private String loginId;

    @Column(name = "client_code", nullable = false, length = 64)
    private String clientCode;

    @Column(name = "jwt_token", length = 2048)
    private String jwtToken;

    @Column(name = "jwt_expires_at")
    private Instant jwtExpiresAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "default_exchange", nullable = false, length = 8)
    private String defaultExchange = "N";

    @Column(name = "default_exchange_type", nullable = false, length = 8)
    private String defaultExchangeType = "C";

    @Column(name = "default_quantity", nullable = false)
    private int defaultQuantity = 1;

    @Column(name = "default_at_market", nullable = false)
    private boolean defaultAtMarket = true;

    @Column(name = "default_is_intraday", nullable = false)
    private boolean defaultIsIntraday = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
