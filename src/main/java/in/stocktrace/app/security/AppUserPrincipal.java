package in.stocktrace.app.security;

import in.stocktrace.app.AppRole;
import in.stocktrace.app.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security principal for an authenticated {@link AppUser}. Carries the
 * primary key so owner-scoped endpoints can look up broker rows without a
 * second email lookup.
 */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final AppRole role;
    private final boolean active;

    public AppUserPrincipal(Long id, String email, String passwordHash, AppRole role, boolean active) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public static AppUserPrincipal of(AppUser u) {
        return new AppUserPrincipal(u.getId(), u.getEmail(), u.getPasswordHash(), u.getRole(), u.isActive());
    }

    public Long getId() {
        return id;
    }

    public AppRole getRole() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
