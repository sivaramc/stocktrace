package in.stocktrace.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAuditRepository extends JpaRepository<OrderAudit, Long> {

    Page<OrderAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<OrderAudit> findByKiteUserIdOrderByCreatedAtDesc(String kiteUserId, Pageable pageable);
}
