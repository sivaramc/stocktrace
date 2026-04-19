package in.stocktrace.kiteapi;

import com.zerodhatech.models.GTT;
import com.zerodhatech.models.GTTParams;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kite/{userId}/gtt")
public class GttController {

    private final KiteApiHelper kite;

    public GttController(KiteApiHelper kite) {
        this.kite = kite;
    }

    @GetMapping
    public List<GTT> list(@PathVariable String userId) {
        return kite.call(userId, k -> k.getGTTs());
    }

    @GetMapping("/{gttId}")
    public GTT get(@PathVariable String userId, @PathVariable int gttId) {
        return kite.call(userId, k -> k.getGTT(gttId));
    }

    @PostMapping
    public GTT place(@PathVariable String userId, @RequestBody GTTParams params) {
        return kite.call(userId, k -> k.placeGTT(params));
    }

    @PutMapping("/{gttId}")
    public GTT modify(@PathVariable String userId, @PathVariable int gttId, @RequestBody GTTParams params) {
        return kite.call(userId, k -> k.modifyGTT(gttId, params));
    }

    @DeleteMapping("/{gttId}")
    public GTT cancel(@PathVariable String userId, @PathVariable int gttId) {
        return kite.call(userId, k -> k.cancelGTT(gttId));
    }
}
