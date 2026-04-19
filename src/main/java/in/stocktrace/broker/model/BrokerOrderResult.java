package in.stocktrace.broker.model;

public record BrokerOrderResult(
        String brokerUserId,
        boolean success,
        String orderId,
        String errorMessage
) {
    public static BrokerOrderResult ok(String brokerUserId, String orderId) {
        return new BrokerOrderResult(brokerUserId, true, orderId, null);
    }

    public static BrokerOrderResult fail(String brokerUserId, String error) {
        return new BrokerOrderResult(brokerUserId, false, null, error);
    }
}
