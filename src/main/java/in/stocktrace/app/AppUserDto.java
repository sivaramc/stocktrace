package in.stocktrace.app;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Request / response shapes for app user auth + admin APIs. */
public final class AppUserDto {

    private AppUserDto() {}

    /** Broker credentials submitted at registration time. Either or both may be populated. */
    public record BrokerProfiles(
            @Valid ZerodhaProfile zerodha,
            @Valid FivePaisaProfile fivepaisa
    ) {}

    public record ZerodhaProfile(
            @NotBlank String brokerUserId,
            @NotBlank String apiKey,
            @NotBlank String apiSecret,
            String label,
            String defaultExchange,
            String defaultProduct,
            String defaultOrderType,
            Integer defaultQuantity,
            String defaultVariety
    ) {}

    public record FivePaisaProfile(
            @NotBlank String brokerUserId,
            @NotBlank String appName,
            @NotBlank String encryptKey,
            @NotBlank String userKey,
            @NotBlank String fivepaisaUserId,
            @NotBlank String password,
            @NotBlank String loginId,
            @NotBlank String clientCode,
            String label,
            String appVer,
            String osName,
            String defaultExchange,
            String defaultExchangeType,
            Integer defaultQuantity,
            Boolean defaultAtMarket,
            Boolean defaultIsIntraday
    ) {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 128) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 128) String displayName,
            @Valid BrokerProfiles brokers
    ) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}

    public record LoginResponse(
            String token,
            long expiresInSeconds,
            Me user
    ) {}

    /** Public profile of the authenticated user. Secrets are never exposed. */
    public record Me(
            Long id,
            String email,
            String displayName,
            AppRole role,
            boolean active,
            boolean hasZerodha,
            boolean hasFivepaisa,
            Instant zerodhaAccessTokenExpiresAt,
            Instant fivepaisaJwtExpiresAt,
            Instant createdAt
    ) {}

    /** Admin-facing row. No secrets. */
    public record AdminRow(
            Long id,
            String email,
            String displayName,
            AppRole role,
            boolean active,
            boolean hasZerodha,
            boolean hasFivepaisa,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ChangeRoleRequest(
            @NotBlank String role
    ) {}
}
