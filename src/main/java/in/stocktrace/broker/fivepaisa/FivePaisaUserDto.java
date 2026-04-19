package in.stocktrace.broker.fivepaisa;

import java.time.Instant;

/**
 * Wire-level views over {@link FivePaisaUser}. Mirrors the pattern used by
 * {@code KiteUserDto} — secrets (password, encryption key, user key, JWT) are
 * NEVER returned verbatim; the response only reveals whether each is present.
 */
public final class FivePaisaUserDto {

    private FivePaisaUserDto() {}

    public record Response(
            Long id,
            String userId,
            String label,
            String appName,
            String appVer,
            String osName,
            String fivepaisaUserId,
            String loginId,
            String clientCode,
            boolean hasEncryptKey,
            boolean hasUserKey,
            boolean hasPassword,
            boolean hasJwt,
            Instant jwtExpiresAt,
            boolean active,
            String defaultExchange,
            String defaultExchangeType,
            int defaultQuantity,
            boolean defaultAtMarket,
            boolean defaultIsIntraday,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(FivePaisaUser u) {
            return new Response(
                    u.getId(),
                    u.getUserId(),
                    u.getLabel(),
                    u.getAppName(),
                    u.getAppVer(),
                    u.getOsName(),
                    u.getFivepaisaUserId(),
                    u.getLoginId(),
                    u.getClientCode(),
                    u.getEncryptKey() != null && !u.getEncryptKey().isBlank(),
                    u.getUserKey() != null && !u.getUserKey().isBlank(),
                    u.getPassword() != null && !u.getPassword().isBlank(),
                    u.getJwtToken() != null && !u.getJwtToken().isBlank(),
                    u.getJwtExpiresAt(),
                    u.isActive(),
                    u.getDefaultExchange(),
                    u.getDefaultExchangeType(),
                    u.getDefaultQuantity(),
                    u.isDefaultAtMarket(),
                    u.isDefaultIsIntraday(),
                    u.getCreatedAt(),
                    u.getUpdatedAt()
            );
        }
    }
}
