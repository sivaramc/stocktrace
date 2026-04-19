package in.stocktrace.user;

import in.stocktrace.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class KiteUserService {

    private final KiteUserRepository repository;

    public KiteUserService(KiteUserRepository repository) {
        this.repository = repository;
    }

    public KiteUser create(KiteUserDto.CreateRequest req) {
        KiteUser u = new KiteUser();
        u.setUserId(req.userId());
        u.setLabel(req.label());
        u.setApiKey(req.apiKey());
        u.setApiSecret(req.apiSecret());
        u.setActive(Boolean.TRUE.equals(req.active()));
        if (req.defaultExchange() != null) u.setDefaultExchange(req.defaultExchange());
        if (req.defaultProduct() != null) u.setDefaultProduct(req.defaultProduct());
        if (req.defaultOrderType() != null) u.setDefaultOrderType(req.defaultOrderType());
        if (req.defaultQuantity() != null) u.setDefaultQuantity(req.defaultQuantity());
        if (req.defaultVariety() != null) u.setDefaultVariety(req.defaultVariety());
        return repository.save(u);
    }

    public KiteUser update(String userId, KiteUserDto.UpdateRequest req) {
        KiteUser u = getRequired(userId);
        if (req.label() != null) u.setLabel(req.label());
        if (req.apiKey() != null) u.setApiKey(req.apiKey());
        if (req.apiSecret() != null) u.setApiSecret(req.apiSecret());
        if (req.active() != null) u.setActive(req.active());
        if (req.defaultExchange() != null) u.setDefaultExchange(req.defaultExchange());
        if (req.defaultProduct() != null) u.setDefaultProduct(req.defaultProduct());
        if (req.defaultOrderType() != null) u.setDefaultOrderType(req.defaultOrderType());
        if (req.defaultQuantity() != null) u.setDefaultQuantity(req.defaultQuantity());
        if (req.defaultVariety() != null) u.setDefaultVariety(req.defaultVariety());
        return repository.save(u);
    }

    public void delete(String userId) {
        KiteUser u = getRequired(userId);
        repository.delete(u);
    }

    public KiteUser setActive(String userId, boolean active) {
        KiteUser u = getRequired(userId);
        u.setActive(active);
        return repository.save(u);
    }

    @Transactional(readOnly = true)
    public KiteUser getRequired(String userId) {
        return repository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Kite user not found: " + userId));
    }

    @Transactional(readOnly = true)
    public List<KiteUser> listAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<KiteUser> listActive() {
        return repository.findAllByActiveTrue();
    }

    public KiteUser saveSession(String userId, String accessToken, String publicToken, java.time.Instant expiresAt) {
        KiteUser u = getRequired(userId);
        u.setAccessToken(accessToken);
        u.setPublicToken(publicToken);
        u.setAccessTokenExpiresAt(expiresAt);
        return repository.save(u);
    }
}
