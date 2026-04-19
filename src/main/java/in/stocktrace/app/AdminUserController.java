package in.stocktrace.app;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints for managing app users. All methods require
 * {@code ROLE_ADMIN} via {@link PreAuthorize}; the request also gets
 * filtered earlier by {@code /api/admin/**} -&gt; {@code hasRole("ADMIN")}
 * in SecurityConfig, so these are defence-in-depth.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AppUserService service;

    public AdminUserController(AppUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<AppUserDto.AdminRow> list() {
        return service.listAll().stream().map(u -> new AppUserDto.AdminRow(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isActive(),
                service.kiteUserOwnedBy(u.getId()).isPresent(),
                service.fivePaisaUserOwnedBy(u.getId()).isPresent(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        )).toList();
    }

    @PostMapping("/{id}/activate")
    public AppUserDto.AdminRow activate(@PathVariable Long id) {
        return row(service.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    public AppUserDto.AdminRow deactivate(@PathVariable Long id) {
        return row(service.setActive(id, false));
    }

    @PostMapping("/{id}/role")
    public AppUserDto.AdminRow changeRole(@PathVariable Long id,
                                          @RequestBody AppUserDto.ChangeRoleRequest req) {
        AppRole role = AppRole.valueOf(req.role().toUpperCase());
        return row(service.setRole(id, role));
    }

    private AppUserDto.AdminRow row(AppUser u) {
        return new AppUserDto.AdminRow(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isActive(),
                service.kiteUserOwnedBy(u.getId()).isPresent(),
                service.fivePaisaUserOwnedBy(u.getId()).isPresent(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}
