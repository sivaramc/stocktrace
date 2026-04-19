package in.stocktrace.broker.fivepaisa;

import in.stocktrace.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class FivePaisaUserService {

    private final FivePaisaUserRepository repo;

    public FivePaisaUserService(FivePaisaUserRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<FivePaisaUser> listAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public List<FivePaisaUser> listActive() {
        return repo.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public FivePaisaUser getRequired(String userId) {
        return repo.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Unknown 5paisa user: " + userId));
    }

    @Transactional
    public FivePaisaUser save(FivePaisaUser user) {
        return repo.save(user);
    }

    @Transactional
    public void delete(String userId) {
        FivePaisaUser u = getRequired(userId);
        repo.delete(u);
    }

    @Transactional
    public FivePaisaUser saveJwt(String userId, String jwt, Instant expiresAt) {
        FivePaisaUser u = getRequired(userId);
        u.setJwtToken(jwt);
        u.setJwtExpiresAt(expiresAt);
        return repo.save(u);
    }

    @Transactional
    public FivePaisaUser setActive(String userId, boolean active) {
        FivePaisaUser u = getRequired(userId);
        u.setActive(active);
        return repo.save(u);
    }
}
