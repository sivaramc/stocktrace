package in.stocktrace.webhook.chartink;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartinkPayloadTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void deserializesSampleChartinkAlert() throws Exception {
        String json = """
                {
                  "stocks": "SEPOWER,ASTEC,EDUCOMP",
                  "trigger_prices": "3.75,541.8,2.1",
                  "triggered_at": "2:34 pm",
                  "scan_name": "Short term breakouts",
                  "scan_url": "short-term-breakouts",
                  "alert_name": "Alert for Short term breakouts",
                  "webhook_url": "http://example.com"
                }
                """;

        ChartinkPayload p = mapper.readValue(json, ChartinkPayload.class);

        assertThat(p.stockList()).containsExactly("SEPOWER", "ASTEC", "EDUCOMP");
        assertThat(p.triggerPriceList()).containsExactly("3.75", "541.8", "2.1");
        assertThat(p.scan_name()).isEqualTo("Short term breakouts");
        assertThat(p.alert_name()).isEqualTo("Alert for Short term breakouts");
    }

    @Test
    void emptyStocksReturnsEmptyArray() {
        ChartinkPayload p = new ChartinkPayload(null, null, null, null, null, null, null);
        assertThat(p.stockList()).isEmpty();
        assertThat(p.triggerPriceList()).isEmpty();
    }
}
