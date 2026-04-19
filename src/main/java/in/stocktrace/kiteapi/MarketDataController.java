package in.stocktrace.kiteapi;

import com.zerodhatech.models.HistoricalData;
import com.zerodhatech.models.Instrument;
import com.zerodhatech.models.LTPQuote;
import com.zerodhatech.models.OHLCQuote;
import com.zerodhatech.models.Quote;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kite/{userId}/marketdata")
public class MarketDataController {

    private final KiteApiHelper kite;

    public MarketDataController(KiteApiHelper kite) {
        this.kite = kite;
    }

    @GetMapping("/instruments")
    public List<Instrument> instruments(@PathVariable String userId,
                                        @RequestParam(required = false) String exchange) {
        if (exchange != null && !exchange.isBlank()) {
            return kite.call(userId, k -> k.getInstruments(exchange));
        }
        return kite.call(userId, k -> k.getInstruments());
    }

    @GetMapping("/quote")
    public Map<String, Quote> quote(@PathVariable String userId,
                                    @RequestParam List<String> i) {
        return kite.call(userId, k -> k.getQuote(i.toArray(new String[0])));
    }

    @GetMapping("/ohlc")
    public Map<String, OHLCQuote> ohlc(@PathVariable String userId,
                                       @RequestParam List<String> i) {
        return kite.call(userId, k -> k.getOHLC(i.toArray(new String[0])));
    }

    @GetMapping("/ltp")
    public Map<String, LTPQuote> ltp(@PathVariable String userId,
                                     @RequestParam List<String> i) {
        return kite.call(userId, k -> k.getLTP(i.toArray(new String[0])));
    }

    @GetMapping("/historical/{instrumentToken}")
    public HistoricalData historical(@PathVariable String userId,
                                     @PathVariable String instrumentToken,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                     @RequestParam(defaultValue = "day") String interval,
                                     @RequestParam(defaultValue = "false") boolean continuous,
                                     @RequestParam(defaultValue = "false") boolean oi) {
        // Kite historical data is IST; interpret caller's LocalDateTime in Asia/Kolkata
        // to stay consistent with hibernate.jdbc.time_zone and the scheduler cron zone.
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        Date fromDate = Date.from(from.atZone(ist).toInstant());
        Date toDate = Date.from(to.atZone(ist).toInstant());
        return kite.call(userId, k -> k.getHistoricalData(fromDate, toDate, instrumentToken, interval, continuous, oi));
    }
}
