package in.stocktrace.app.security;

import in.stocktrace.app.AppUser;
import in.stocktrace.app.AppUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository repo;

    public AppUserDetailsService(AppUserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser u = repo.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No such user: " + email));
        return AppUserPrincipal.of(u);
    }
}
