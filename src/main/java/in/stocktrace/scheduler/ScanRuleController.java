package in.stocktrace.scheduler;

import in.stocktrace.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scan-rules")
public class ScanRuleController {

    private final ScanRuleRepository repository;

    public ScanRuleController(ScanRuleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ScanRuleDto.Response> list() {
        return repository.findAll().stream().map(ScanRuleDto.Response::from).toList();
    }

    @GetMapping("/{id}")
    public ScanRuleDto.Response get(@PathVariable Long id) {
        return ScanRuleDto.Response.from(findOrThrow(id));
    }

    @PostMapping
    public ResponseEntity<ScanRuleDto.Response> create(@Valid @RequestBody ScanRuleDto.CreateRequest req) {
        ScanRule r = new ScanRule();
        apply(r, req);
        repository.save(r);
        return ResponseEntity.status(201).body(ScanRuleDto.Response.from(r));
    }

    @PutMapping("/{id}")
    public ScanRuleDto.Response update(@PathVariable Long id, @Valid @RequestBody ScanRuleDto.CreateRequest req) {
        ScanRule r = findOrThrow(id);
        apply(r, req);
        repository.save(r);
        return ScanRuleDto.Response.from(r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private ScanRule findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Scan rule not found: " + id));
    }

    private static void apply(ScanRule r, ScanRuleDto.CreateRequest req) {
        r.setName(req.name());
        r.setExchange(req.exchange());
        r.setTradingsymbol(req.tradingsymbol());
        r.setInstrumentToken(req.instrumentToken());
        r.setConditionType(req.conditionType());
        r.setThresholdLow(req.thresholdLow());
        r.setThresholdHigh(req.thresholdHigh());
        if (req.transactionType() != null) r.setTransactionType(req.transactionType());
        if (req.quantity() != null) r.setQuantity(req.quantity());
        if (req.product() != null) r.setProduct(req.product());
        if (req.orderType() != null) r.setOrderType(req.orderType());
        if (req.active() != null) r.setActive(req.active());
    }
}
