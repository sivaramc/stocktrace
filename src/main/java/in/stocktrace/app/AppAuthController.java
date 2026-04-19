package in.stocktrace.app;

import in.stocktrace.app.security.AppUserPrincipal;
import in.stocktrace.app.security.JwtService;
import in.stocktrace.broker.fivepaisa.FivePaisaUser;
import in.stocktrace.user.KiteUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Public + me endpoints for app-level authentication.
 *
 * <ul>
 *   <li>{@code POST /api/auth/register} — creates an inactive {@link AppUser} with optional broker credentials.</li>
 *   <li>{@code POST /api/auth/login} — exchanges email/password for a stocktrace session JWT.</li>
 *   <li>{@code GET  /api/auth/me} — returns the authenticated user's profile.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AppAuthController {

    private final AppUserService service;
    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AppAuthController(AppUserService service, AuthenticationManager authManager, JwtService jwt) {
        this.service = service;
        this.authManager = authManager;
        this.jwt = jwt;
    }

    @PostMapping("/register")
    public ResponseEntity<AppUserDto.Me> register(@Valid @RequestBody AppUserDto.RegisterRequest req) {
        AppUser u = service.register(req);
        return ResponseEntity.status(201).body(me(u));
    }

    @PostMapping("/login")
    public AppUserDto.LoginResponse login(@Valid @RequestBody AppUserDto.LoginRequest req) {
        Authentication auth;
        try {
            auth = authManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (DisabledException ex) {
            throw new AccountInactiveException("Account not yet activated by an administrator");
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        AppUser u = service.getRequired(principal.getId());
        String token = jwt.issue(u);
        return new AppUserDto.LoginResponse(token, jwt.ttlSeconds(), me(u));
    }

    @GetMapping("/me")
    public AppUserDto.Me me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return me(service.getRequired(principal.getId()));
    }

    private AppUserDto.Me me(AppUser u) {
        Optional<KiteUser> k = service.kiteUserOwnedBy(u.getId());
        Optional<FivePaisaUser> f = service.fivePaisaUserOwnedBy(u.getId());
        return new AppUserDto.Me(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getRole(),
                u.isActive(),
                k.isPresent(),
                f.isPresent(),
                k.map(KiteUser::getAccessTokenExpiresAt).orElse(null),
                f.map(FivePaisaUser::getJwtExpiresAt).orElse(null),
                u.getCreatedAt()
        );
    }

    /** Thrown when the caller tries to log in before activation. */
    public static class AccountInactiveException extends RuntimeException {
        public AccountInactiveException(String m) { super(m); }
    }

    public static class BadCredentialsException extends RuntimeException {
        public BadCredentialsException(String m) { super(m); }
    }
}
