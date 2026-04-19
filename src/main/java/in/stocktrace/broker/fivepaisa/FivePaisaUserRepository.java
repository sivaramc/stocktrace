package in.stocktrace.broker.fivepaisa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FivePaisaUserRepository extends JpaRepository<FivePaisaUser, Long> {

    Optional<FivePaisaUser> findByUserId(String userId);

    List<FivePaisaUser> findAllByActiveTrue();
}
