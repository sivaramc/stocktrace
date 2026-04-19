package in.stocktrace.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KiteUserRepository extends JpaRepository<KiteUser, Long> {

    Optional<KiteUser> findByUserId(String userId);

    List<KiteUser> findAllByActiveTrue();

    Optional<KiteUser> findByOwnerAppUserId(Long ownerAppUserId);

    List<KiteUser> findAllByOwnerAppUserId(Long ownerAppUserId);
}
