package in.stocktrace.kiteapi;

import com.zerodhatech.models.CombinedMarginData;
import com.zerodhatech.models.ContractNote;
import com.zerodhatech.models.ContractNoteParams;
import com.zerodhatech.models.Margin;
import com.zerodhatech.models.MarginCalculationData;
import com.zerodhatech.models.MarginCalculationParams;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kite/{userId}/margins")
public class MarginController {

    private final KiteApiHelper kite;

    public MarginController(KiteApiHelper kite) {
        this.kite = kite;
    }

    @GetMapping
    public Map<String, Margin> allMargins(@PathVariable String userId) {
        return kite.call(userId, k -> k.getMargins());
    }

    @GetMapping("/{segment}")
    public Margin marginBySegment(@PathVariable String userId, @PathVariable String segment) {
        return kite.call(userId, k -> k.getMargins(segment));
    }

    @PostMapping("/calc")
    public List<MarginCalculationData> calcMargins(@PathVariable String userId,
                                                   @RequestBody List<MarginCalculationParams> params) {
        return kite.call(userId, k -> k.getMarginCalculation(params));
    }

    @PostMapping("/calc/combined")
    public CombinedMarginData calcCombined(@PathVariable String userId,
                                           @RequestParam(defaultValue = "false") boolean considerPositions,
                                           @RequestParam(defaultValue = "false") boolean compactMode,
                                           @RequestBody List<MarginCalculationParams> params) {
        return kite.call(userId, k -> k.getCombinedMarginCalculation(params, considerPositions, compactMode));
    }

    @PostMapping("/virtual-contract-note")
    public List<ContractNote> virtualContractNote(@PathVariable String userId,
                                                  @RequestBody List<ContractNoteParams> params) {
        return kite.call(userId, k -> k.getVirtualContractNote(params));
    }
}
