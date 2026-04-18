package in.stocktrace.scheduler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanRuleTest {

    @Test
    void ltpAboveMatchesWhenPriceExceedsHighThreshold() {
        ScanRule r = new ScanRule();
        r.setConditionType(ScanRule.ConditionType.LTP_ABOVE);
        r.setThresholdHigh(100.0);

        assertThat(r.matches(101.0)).isTrue();
        assertThat(r.matches(99.0)).isFalse();
        assertThat(r.matches(100.0)).isFalse();
    }

    @Test
    void ltpBelowMatchesWhenPriceUnderLowThreshold() {
        ScanRule r = new ScanRule();
        r.setConditionType(ScanRule.ConditionType.LTP_BELOW);
        r.setThresholdLow(50.0);

        assertThat(r.matches(49.99)).isTrue();
        assertThat(r.matches(50.0)).isFalse();
        assertThat(r.matches(51.0)).isFalse();
    }

    @Test
    void ltpBetweenMatchesInclusive() {
        ScanRule r = new ScanRule();
        r.setConditionType(ScanRule.ConditionType.LTP_BETWEEN);
        r.setThresholdLow(10.0);
        r.setThresholdHigh(20.0);

        assertThat(r.matches(10.0)).isTrue();
        assertThat(r.matches(15.0)).isTrue();
        assertThat(r.matches(20.0)).isTrue();
        assertThat(r.matches(9.9)).isFalse();
        assertThat(r.matches(20.1)).isFalse();
    }

    @Test
    void instrumentKeyCombinesExchangeAndSymbol() {
        ScanRule r = new ScanRule();
        r.setExchange("NSE");
        r.setTradingsymbol("INFY");
        assertThat(r.instrumentKey()).isEqualTo("NSE:INFY");
    }
}
