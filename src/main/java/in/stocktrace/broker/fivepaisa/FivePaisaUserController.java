package in.stocktrace.broker.fivepaisa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** CRUD over registered 5paisa accounts. */
@RestController
@RequestMapping("/api/5paisa/users")
public class FivePaisaUserController {

    private final FivePaisaUserService service;
    private final FivePaisaClientFactory factory;

    public FivePaisaUserController(FivePaisaUserService service, FivePaisaClientFactory factory) {
        this.service = service;
        this.factory = factory;
    }

    @GetMapping
    public List<FivePaisaUserDto.Response> list() {
        return service.listAll().stream().map(FivePaisaUserDto.Response::from).toList();
    }

    @GetMapping("/{userId}")
    public FivePaisaUserDto.Response get(@PathVariable String userId) {
        return FivePaisaUserDto.Response.from(service.getRequired(userId));
    }

    @PostMapping
    public FivePaisaUserDto.Response create(@Valid @RequestBody UpsertRequest req) {
        FivePaisaUser u = new FivePaisaUser();
        applyTo(u, req);
        u.setUserId(req.userId());
        return FivePaisaUserDto.Response.from(service.save(u));
    }

    @PutMapping("/{userId}")
    public FivePaisaUserDto.Response update(@PathVariable String userId, @Valid @RequestBody UpsertRequest req) {
        FivePaisaUser u = service.getRequired(userId);
        applyTo(u, req);
        FivePaisaUser saved = service.save(u);
        factory.evict(userId);
        return FivePaisaUserDto.Response.from(saved);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> delete(@PathVariable String userId) {
        service.delete(userId);
        factory.evict(userId);
        return ResponseEntity.noContent().build();
    }

    private static void applyTo(FivePaisaUser u, UpsertRequest req) {
        u.setLabel(req.label());
        u.setAppName(req.appName());
        if (req.appVer() != null) u.setAppVer(req.appVer());
        if (req.osName() != null) u.setOsName(req.osName());
        u.setEncryptKey(req.encryptKey());
        u.setUserKey(req.userKey());
        u.setFivepaisaUserId(req.fivepaisaUserId());
        u.setPassword(req.password());
        u.setLoginId(req.loginId());
        u.setClientCode(req.clientCode());
        if (req.active() != null) u.setActive(req.active());
        if (req.defaultExchange() != null) u.setDefaultExchange(req.defaultExchange());
        if (req.defaultExchangeType() != null) u.setDefaultExchangeType(req.defaultExchangeType());
        if (req.defaultQuantity() != null) u.setDefaultQuantity(req.defaultQuantity());
        if (req.defaultAtMarket() != null) u.setDefaultAtMarket(req.defaultAtMarket());
        if (req.defaultIsIntraday() != null) u.setDefaultIsIntraday(req.defaultIsIntraday());
    }

    public record UpsertRequest(
            @NotBlank String userId,
            String label,
            @NotBlank String appName,
            String appVer,
            String osName,
            @NotBlank String encryptKey,
            @NotBlank String userKey,
            @NotBlank String fivepaisaUserId,
            @NotBlank String password,
            @NotBlank String loginId,
            @NotBlank String clientCode,
            Boolean active,
            String defaultExchange,
            String defaultExchangeType,
            Integer defaultQuantity,
            Boolean defaultAtMarket,
            Boolean defaultIsIntraday
    ) {}
}
