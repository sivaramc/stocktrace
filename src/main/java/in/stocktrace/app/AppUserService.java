package in.stocktrace.app;

import in.stocktrace.broker.fivepaisa.FivePaisaUser;
import in.stocktrace.broker.fivepaisa.FivePaisaUserRepository;
import in.stocktrace.common.NotFoundException;
import in.stocktrace.user.KiteUser;
import in.stocktrace.user.KiteUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * CRUD + lookup service for {@link AppUser} rows plus helpers for linking to
 * broker credential rows ({@link KiteUser}, {@link FivePaisaUser}).
 */
@Service
@Transactional
public class AppUserService {

    private final AppUserRepository repo;
    private final PasswordEncoder encoder;
    private final KiteUserRepository kiteUsers;
    private final FivePaisaUserRepository fivePaisaUsers;

    public AppUserService(AppUserRepository repo,
                          PasswordEncoder encoder,
                          KiteUserRepository kiteUsers,
                          FivePaisaUserRepository fivePaisaUsers) {
        this.repo = repo;
        this.encoder = encoder;
        this.kiteUsers = kiteUsers;
        this.fivePaisaUsers = fivePaisaUsers;
    }

    public AppUser create(String email, String rawPassword, String displayName, AppRole role, boolean active) {
        if (repo.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }
        AppUser u = new AppUser();
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setDisplayName(displayName);
        u.setRole(role);
        u.setActive(active);
        return repo.save(u);
    }

    /**
     * Registration entrypoint: create the {@link AppUser} (inactive until an
     * admin activates) together with any broker credential rows supplied. Each
     * broker row is linked back to the new {@link AppUser} via its owner FK.
     */
    public AppUser register(AppUserDto.RegisterRequest req) {
        AppUser u = create(req.email(), req.password(), req.displayName(), AppRole.USER, false);
        if (req.brokers() != null) {
            if (req.brokers().zerodha() != null) {
                attachZerodha(u, req.brokers().zerodha());
            }
            if (req.brokers().fivepaisa() != null) {
                attachFivePaisa(u, req.brokers().fivepaisa());
            }
        }
        return u;
    }

    private void attachZerodha(AppUser owner, AppUserDto.ZerodhaProfile p) {
        String brokerUserId = p.brokerUserId();
        if (kiteUsers.findByUserId(brokerUserId).isPresent()) {
            throw new IllegalArgumentException("Zerodha userId already in use: " + brokerUserId);
        }
        KiteUser k = new KiteUser();
        k.setUserId(brokerUserId);
        k.setOwnerAppUserId(owner.getId());
        k.setLabel(p.label());
        k.setApiKey(p.apiKey());
        k.setApiSecret(p.apiSecret());
        k.setActive(false);
        if (p.defaultExchange() != null) k.setDefaultExchange(p.defaultExchange());
        if (p.defaultProduct() != null) k.setDefaultProduct(p.defaultProduct());
        if (p.defaultOrderType() != null) k.setDefaultOrderType(p.defaultOrderType());
        if (p.defaultQuantity() != null) k.setDefaultQuantity(p.defaultQuantity());
        if (p.defaultVariety() != null) k.setDefaultVariety(p.defaultVariety());
        kiteUsers.save(k);
    }

    private void attachFivePaisa(AppUser owner, AppUserDto.FivePaisaProfile p) {
        String brokerUserId = p.brokerUserId();
        if (fivePaisaUsers.findByUserId(brokerUserId).isPresent()) {
            throw new IllegalArgumentException("5paisa userId already in use: " + brokerUserId);
        }
        FivePaisaUser f = new FivePaisaUser();
        f.setUserId(brokerUserId);
        f.setOwnerAppUserId(owner.getId());
        f.setLabel(p.label());
        f.setAppName(p.appName());
        if (p.appVer() != null) f.setAppVer(p.appVer());
        if (p.osName() != null) f.setOsName(p.osName());
        f.setEncryptKey(p.encryptKey());
        f.setUserKey(p.userKey());
        f.setFivepaisaUserId(p.fivepaisaUserId());
        f.setPassword(p.password());
        f.setLoginId(p.loginId());
        f.setClientCode(p.clientCode());
        f.setActive(false);
        if (p.defaultExchange() != null) f.setDefaultExchange(p.defaultExchange());
        if (p.defaultExchangeType() != null) f.setDefaultExchangeType(p.defaultExchangeType());
        if (p.defaultQuantity() != null) f.setDefaultQuantity(p.defaultQuantity());
        if (p.defaultAtMarket() != null) f.setDefaultAtMarket(p.defaultAtMarket());
        if (p.defaultIsIntraday() != null) f.setDefaultIsIntraday(p.defaultIsIntraday());
        fivePaisaUsers.save(f);
    }

    public AppUser setActive(Long id, boolean active) {
        AppUser u = getRequired(id);
        u.setActive(active);
        return repo.save(u);
    }

    public AppUser setRole(Long id, AppRole role) {
        AppUser u = getRequired(id);
        u.setRole(role);
        return repo.save(u);
    }

    @Transactional(readOnly = true)
    public AppUser getRequired(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NotFoundException("App user not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findByEmail(String email) {
        return repo.findByEmailIgnoreCase(email);
    }

    @Transactional(readOnly = true)
    public List<AppUser> listAll() {
        return repo.findAll();
    }

    // ---- ownership helpers -------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<KiteUser> kiteUserOwnedBy(Long appUserId) {
        return kiteUsers.findByOwnerAppUserId(appUserId);
    }

    @Transactional(readOnly = true)
    public Optional<FivePaisaUser> fivePaisaUserOwnedBy(Long appUserId) {
        return fivePaisaUsers.findByOwnerAppUserId(appUserId);
    }

    /**
     * Ensures the given {@code brokerUserId} (kite user_id) belongs to
     * {@code appUserId}. Used by self-trade / broker-ops endpoints.
     */
    @Transactional(readOnly = true)
    public KiteUser requireOwnedKiteUser(Long appUserId, String brokerUserId) {
        KiteUser u = kiteUsers.findByUserId(brokerUserId)
                .orElseThrow(() -> new NotFoundException("Kite user not found: " + brokerUserId));
        if (u.getOwnerAppUserId() == null || !u.getOwnerAppUserId().equals(appUserId)) {
            throw new NotFoundException("Kite user not owned by current user: " + brokerUserId);
        }
        return u;
    }

    @Transactional(readOnly = true)
    public FivePaisaUser requireOwnedFivePaisaUser(Long appUserId, String brokerUserId) {
        FivePaisaUser u = fivePaisaUsers.findByUserId(brokerUserId)
                .orElseThrow(() -> new NotFoundException("5paisa user not found: " + brokerUserId));
        if (u.getOwnerAppUserId() == null || !u.getOwnerAppUserId().equals(appUserId)) {
            throw new NotFoundException("5paisa user not owned by current user: " + brokerUserId);
        }
        return u;
    }
}
