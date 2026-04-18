package com.agrimarket.config;

import com.agrimarket.domain.UserAccount;
import com.agrimarket.domain.UserRole;
import com.agrimarket.repo.UserAccountRepository;
import com.agrimarket.service.BootstrapService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Optional startup seed for the first platform admin (e.g. local development). Runs after demo data seed so the
 * marketplace can still be populated when {@code app.seed-demo-data} is true.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class DefaultAdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminSeeder.class);

    private final DefaultAdminProperties props;
    private final BootstrapService bootstrapService;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!props.isSeed()) {
            return;
        }
        if (!bootstrapService.needsFirstAdmin()) {
            return;
        }
        String email = props.getEmail() == null ? "" : props.getEmail().trim();
        if (email.isEmpty()) {
            log.warn("app.default-admin.seed is true but app.default-admin.email is empty; skipping default admin seed");
            return;
        }
        String rawPw = props.getPassword() == null ? "" : props.getPassword();
        if (rawPw.isEmpty()) {
            log.warn("app.default-admin.password is empty; skipping default admin seed");
            return;
        }
        UserAccount admin = new UserAccount(email, passwordEncoder.encode(rawPw), UserRole.PLATFORM_ADMIN, null);
        String name = props.getDisplayName();
        if (name != null && !name.isBlank()) {
            admin.setDisplayName(name.trim());
        }
        userAccountRepository.save(admin);
        log.info("Seeded default platform administrator: {} ({})", email, admin.getDisplayName());
    }
}
