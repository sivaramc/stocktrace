package in.stocktrace.app.security;

import in.stocktrace.app.AppRole;
import in.stocktrace.app.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Signs and verifies stocktrace session JWTs (distinct from broker JWTs like
 * the 5paisa trading-day token).
 *
 * <p>Subject = app user id; includes {@code email} and {@code role} as custom
 * claims so the JWT filter can build the principal without a DB hit on every
 * request.
 */
@Service
public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final long ttlSeconds;
    private final String issuer;

    public JwtService(
            @Value("${stocktrace.auth.jwt.secret}") String secret,
            @Value("${stocktrace.auth.jwt.ttl-seconds:28800}") long ttlSeconds,
            @Value("${stocktrace.auth.jwt.issuer:stocktrace}") String issuer) {
        // Require at least 256 bits of key material for HS256.
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "stocktrace.auth.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(raw);
        this.ttlSeconds = ttlSeconds;
        this.issuer = issuer;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofSeconds(ttlSeconds));
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
    }

    public ParsedToken parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        Claims c = jws.getPayload();
        Long userId = Long.valueOf(c.getSubject());
        String email = c.get(CLAIM_EMAIL, String.class);
        String role = c.get(CLAIM_ROLE, String.class);
        return new ParsedToken(userId, email, AppRole.valueOf(role));
    }

    public record ParsedToken(Long appUserId, String email, AppRole role) {}
}
