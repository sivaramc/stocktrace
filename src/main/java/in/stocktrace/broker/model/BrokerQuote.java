package in.stocktrace.broker.model;

public record BrokerQuote(
        String instrument,      // e.g. NSE:INFY
        Long instrumentToken,
        Double lastPrice,
        Double open,
        Double high,
        Double low,
        Double close,
        Long volume
) {}
