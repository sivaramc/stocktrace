package in.stocktrace.broker.model;

/**
 * Broker-agnostic order request. Nulls are allowed and each broker
 * implementation may substitute user-specific defaults.
 */
public record BrokerOrderRequest(
        String tradingsymbol,
        String exchange,
        String transactionType,   // BUY / SELL
        String orderType,         // MARKET / LIMIT / SL / SL-M
        String product,           // CNC / MIS / NRML
        String variety,           // regular / amo / co / bo / iceberg / auction
        String validity,          // DAY / IOC / TTL
        Integer quantity,
        Double price,
        Double triggerPrice,
        Integer disclosedQuantity,
        String tag
) {}
