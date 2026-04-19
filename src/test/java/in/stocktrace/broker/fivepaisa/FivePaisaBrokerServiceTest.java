package in.stocktrace.broker.fivepaisa;

import in.stocktrace.common.BrokerOperationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FivePaisaBrokerServiceTest {

    @Test
    void mapExchangeTranslatesHumanNamesToOneLetterCodes() {
        assertThat(FivePaisaBrokerService.mapExchange("NSE")).isEqualTo("N");
        assertThat(FivePaisaBrokerService.mapExchange("BSE")).isEqualTo("B");
        assertThat(FivePaisaBrokerService.mapExchange("MCX")).isEqualTo("M");
    }

    @Test
    void mapExchangeAcceptsOneLetterCodes() {
        assertThat(FivePaisaBrokerService.mapExchange("N")).isEqualTo("N");
        assertThat(FivePaisaBrokerService.mapExchange("B")).isEqualTo("B");
        assertThat(FivePaisaBrokerService.mapExchange("M")).isEqualTo("M");
    }

    @Test
    void mapExchangeDefaultsToNseWhenNull() {
        assertThat(FivePaisaBrokerService.mapExchange(null)).isEqualTo("N");
    }

    @Test
    void brokerIdIsFivePaisa() {
        FivePaisaBrokerService svc = new FivePaisaBrokerService(null, null);
        assertThat(svc.id()).isEqualTo("5paisa");
    }

    @Test
    void parseScripCodeParsesNumericTradingsymbol() {
        assertThat(FivePaisaBrokerService.parseScripCode("1594")).isEqualTo(1594);
        assertThat(FivePaisaBrokerService.parseScripCode("  35019  ")).isEqualTo(35019);
    }

    @Test
    void parseScripCodeRejectsNonNumericTradingsymbol() {
        assertThatThrownBy(() -> FivePaisaBrokerService.parseScripCode("INFY"))
                .isInstanceOf(BrokerOperationException.class)
                .hasMessageContaining("numeric ScripCode");
    }

    @Test
    void parseScripCodeRejectsBlank() {
        assertThatThrownBy(() -> FivePaisaBrokerService.parseScripCode(""))
                .isInstanceOf(BrokerOperationException.class);
        assertThatThrownBy(() -> FivePaisaBrokerService.parseScripCode(null))
                .isInstanceOf(BrokerOperationException.class);
    }

    @Test
    void isValidScripCodeAcceptsPositiveNumericStrings() {
        assertThat(FivePaisaBrokerService.isValidScripCode("1594")).isTrue();
        assertThat(FivePaisaBrokerService.isValidScripCode(" 42 ")).isTrue();
    }

    @Test
    void isValidScripCodeRejectsNonNumericAndNonPositive() {
        assertThat(FivePaisaBrokerService.isValidScripCode("INFY")).isFalse();
        assertThat(FivePaisaBrokerService.isValidScripCode("")).isFalse();
        assertThat(FivePaisaBrokerService.isValidScripCode(null)).isFalse();
        assertThat(FivePaisaBrokerService.isValidScripCode("0")).isFalse();
        assertThat(FivePaisaBrokerService.isValidScripCode("-5")).isFalse();
    }
}
