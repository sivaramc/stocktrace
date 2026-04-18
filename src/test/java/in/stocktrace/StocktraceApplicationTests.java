package in.stocktrace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StocktraceApplicationTests {

    @Test
    void contextLoads() {
        // Smoke test: verify the Spring context starts with the test profile.
    }
}
