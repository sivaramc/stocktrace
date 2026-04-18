package in.stocktrace.common;

public class BrokerOperationException extends RuntimeException {
    public BrokerOperationException(String message) {
        super(message);
    }

    public BrokerOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
