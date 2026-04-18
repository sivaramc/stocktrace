package in.stocktrace.webhook.chartink;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Page<WebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
