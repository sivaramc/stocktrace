package in.stocktrace.scheduler;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanRuleRepository extends JpaRepository<ScanRule, Long> {
    List<ScanRule> findAllByActiveTrue();
}
