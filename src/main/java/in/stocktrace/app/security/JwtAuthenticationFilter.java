package in.stocktrace.app.security;

import in.stocktrace.app.AppUser;
import in.stocktrace.app.AppUserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Parses the {@code Authorization: Bearer <jwt>} header and, when valid,
 * installs an {@link AppUserPrincipal} into the Spring Security context. We
 * do one DB hit per request to honour {@code active=false} flipping without
 * waiting for token expiry.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwt;
    private final AppUserRepository users;

    public JwtAuthenticationFilter(JwtService jwt, AppUserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                JwtService.ParsedToken parsed = jwt.parse(token);
                Optional<AppUser> maybe = users.findById(parsed.appUserId());
                if (maybe.isPresent() && maybe.get().isActive()) {
                    AppUserPrincipal principal = AppUserPrincipal.of(maybe.get());
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException ex) {
                log.debug("Rejected JWT on {}: {}", request.getRequestURI(), ex.getMessage());
                // Leave context unauthenticated; SecurityConfig will 401 if required.
            }
        }
        chain.doFilter(request, response);
    }
}
