package in.stocktrace.webhook.chartink;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Chartink's alert webhook POST body. See
 * https://chartink.com/articles/alerts/webhook-support-for-alerts/
 *
 * <pre>
 * {
 *   "stocks": "SEPOWER,ASTEC,EDUCOMP",
 *   "trigger_prices": "3.75,541.8,2.1",
 *   "triggered_at": "2:34 pm",
 *   "scan_name": "Short term breakouts",
 *   "scan_url": "short-term-breakouts",
 *   "alert_name": "Alert for Short term breakouts",
 *   "webhook_url": "http://..."
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChartinkPayload(
        String stocks,
        String trigger_prices,
        String triggered_at,
        String scan_name,
        String scan_url,
        String alert_name,
        String webhook_url
) {
    public String[] stockList() {
        if (stocks == null || stocks.isBlank()) return new String[0];
        return stocks.split("\\s*,\\s*");
    }

    public String[] triggerPriceList() {
        if (trigger_prices == null || trigger_prices.isBlank()) return new String[0];
        return trigger_prices.split("\\s*,\\s*");
    }
}
