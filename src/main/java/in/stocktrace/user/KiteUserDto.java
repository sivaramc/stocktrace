package in.stocktrace.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

public class KiteUserDto {

    public record CreateRequest(
            @NotBlank String userId,
            String label,
            @NotBlank String apiKey,
            @NotBlank String apiSecret,
            Boolean active,
            String defaultExchange,
            String defaultProduct,
            String defaultOrderType,
            @Positive Integer defaultQuantity,
            String defaultVariety
    ) {}

    public record UpdateRequest(
            String label,
            String apiKey,
            String apiSecret,
            Boolean active,
            String defaultExchange,
            String defaultProduct,
            String defaultOrderType,
            Integer defaultQuantity,
            String defaultVariety
    ) {}

    public record Response(
            Long id,
            String userId,
            String label,
            String apiKey,
            boolean hasApiSecret,
            boolean hasAccessToken,
            Instant accessTokenExpiresAt,
            boolean active,
            String defaultExchange,
            String defaultProduct,
            String defaultOrderType,
            int defaultQuantity,
            String defaultVariety,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(KiteUser u) {
            return new Response(
                    u.getId(),
                    u.getUserId(),
                    u.getLabel(),
                    u.getApiKey(),
                    u.getApiSecret() != null && !u.getApiSecret().isBlank(),
                    u.getAccessToken() != null && !u.getAccessToken().isBlank(),
                    u.getAccessTokenExpiresAt(),
                    u.isActive(),
                    u.getDefaultExchange(),
                    u.getDefaultProduct(),
                    u.getDefaultOrderType(),
                    u.getDefaultQuantity(),
                    u.getDefaultVariety(),
                    u.getCreatedAt(),
                    u.getUpdatedAt()
            );
        }
    }
}
