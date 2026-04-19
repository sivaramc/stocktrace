package in.stocktrace.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.stocktrace.broker.model.BrokerOrderRequest;
import in.stocktrace.broker.model.BrokerOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderAuditService {

    private static final Logger log = LoggerFactory.getLogger(OrderAuditService.class);

    private final OrderAuditRepository repository;
    private final ObjectMapper objectMapper;

    public OrderAuditService(OrderAuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public OrderAudit record(String source,
                             String kiteUserId,
                             BrokerOrderRequest request,
                             BrokerOrderResult result) {
        return record("zerodha", source, kiteUserId, request, result);
    }

    public OrderAudit record(String broker,
                             String source,
                             String kiteUserId,
                             BrokerOrderRequest request,
                             BrokerOrderResult result) {
        OrderAudit a = new OrderAudit();
        a.setBroker(broker);
        a.setKiteUserId(kiteUserId);
        a.setSource(source);
        a.setTradingsymbol(n(request.tradingsymbol()));
        a.setExchange(n(request.exchange()));
        a.setTransactionType(n(request.transactionType()));
        a.setOrderType(n(request.orderType()));
        a.setProduct(n(request.product()));
        a.setQuantity(request.quantity() == null ? 0 : request.quantity());
        a.setPrice(request.price());
        a.setTriggerPrice(request.triggerPrice());
        a.setBrokerOrderId(result.orderId());
        a.setStatus(result.success() ? "SUCCESS" : "FAILED");
        a.setErrorMessage(result.errorMessage());
        a.setRequestJson(writeJson(request));
        a.setResponseJson(writeJson(result));
        return repository.save(a);
    }

    @Transactional(readOnly = true)
    public Page<OrderAudit> list(String kiteUserId, Pageable pageable) {
        if (kiteUserId == null || kiteUserId.isBlank()) {
            return repository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return repository.findByKiteUserIdOrderByCreatedAtDesc(kiteUserId, pageable);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            log.debug("Unable to serialize audit payload", e);
            return null;
        }
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }
}
