package in.stocktrace.scheduler;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class ScanRuleDto {

    public record CreateRequest(
            @NotBlank String name,
            @NotBlank String exchange,
            @NotBlank String tradingsymbol,
            Long instrumentToken,
            @NotNull ScanRule.ConditionType conditionType,
            Double thresholdLow,
            Double thresholdHigh,
            String transactionType,
            Integer quantity,
            String product,
            String orderType,
            Boolean active
    ) {}

    public record Response(
            Long id,
            String name,
            String exchange,
            String tradingsymbol,
            Long instrumentToken,
            ScanRule.ConditionType conditionType,
            Double thresholdLow,
            Double thresholdHigh,
            String transactionType,
            int quantity,
            String product,
            String orderType,
            boolean active,
            Instant lastTriggeredAt
    ) {
        public static Response from(ScanRule r) {
            return new Response(
                    r.getId(), r.getName(), r.getExchange(), r.getTradingsymbol(),
                    r.getInstrumentToken(), r.getConditionType(), r.getThresholdLow(), r.getThresholdHigh(),
                    r.getTransactionType(), r.getQuantity(), r.getProduct(), r.getOrderType(),
                    r.isActive(), r.getLastTriggeredAt()
            );
        }
    }
}
