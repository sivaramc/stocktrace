package in.stocktrace.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit/orders")
public class OrderAuditController {

    private final OrderAuditService service;

    public OrderAuditController(OrderAuditService service) {
        this.service = service;
    }

    @GetMapping
    public Page<OrderAudit> list(@RequestParam(required = false) String userId,
                                 @PageableDefault(size = 50) Pageable pageable) {
        return service.list(userId, pageable);
    }
}
