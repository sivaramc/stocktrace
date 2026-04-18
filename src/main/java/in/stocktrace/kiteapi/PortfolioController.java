package in.stocktrace.kiteapi;

import com.zerodhatech.models.AuctionInstrument;
import com.zerodhatech.models.Holding;
import com.zerodhatech.models.Position;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kite/{userId}")
public class PortfolioController {

    private final KiteApiHelper kite;

    public PortfolioController(KiteApiHelper kite) {
        this.kite = kite;
    }

    @GetMapping("/holdings")
    public List<Holding> holdings(@PathVariable String userId) {
        return kite.call(userId, k -> k.getHoldings());
    }

    @GetMapping("/positions")
    public Map<String, List<Position>> positions(@PathVariable String userId) {
        return kite.call(userId, k -> k.getPositions());
    }

    @PostMapping("/positions/convert")
    public Map<String, Object> convertPosition(@PathVariable String userId,
                                               @RequestBody ConvertPositionRequest req) {
        JSONObject resp = kite.call(userId, k -> k.convertPosition(
                req.tradingSymbol(),
                req.exchange(),
                req.transactionType(),
                req.positionType(),
                req.oldProduct(),
                req.newProduct(),
                req.quantity()));
        return resp == null ? Map.of() : resp.toMap();
    }

    @GetMapping("/auction-instruments")
    public List<AuctionInstrument> auctionInstruments(@PathVariable String userId) {
        return kite.call(userId, k -> k.getAuctionInstruments());
    }

    public record ConvertPositionRequest(
            String tradingSymbol,
            String exchange,
            String transactionType,
            String positionType,
            String oldProduct,
            String newProduct,
            int quantity
    ) {}
}
