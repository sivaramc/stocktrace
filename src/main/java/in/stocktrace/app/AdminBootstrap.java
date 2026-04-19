package in.stocktrace.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds the first admin on startup when {@code stocktrace.admin.email} and
 * {@code stocktrace.admin.password} are configured. Idempotent: a matching
 * row is left alone, so restarts don't overwrite a rotated password.
 */
@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserService service;
    private final String email;
    private final String password;
    private final String displayName;

    public AdminBootstrap(AppUserService service,
                          @Value("${stocktrace.admin.email:}") String email,
                          @Value("${stocktrace.admin.password:}") String password,
                          @Value("${stocktrace.admin.display-name:Administrator}") String displayName) {
        this.service = service;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    public void run(String... args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.info("Admin bootstrap skipped (stocktrace.admin.email/password not set)");
            return;
        }
        if (service.findByEmail(email).isPresent()) {
            log.info("Admin bootstrap: {} already present, leaving untouched", email);
            return;
        }
        service.create(email, password, displayName, AppRole.ADMIN, true);
        log.warn("Admin bootstrap: created initial admin {} — rotate the password after first login", email);
    }
}
