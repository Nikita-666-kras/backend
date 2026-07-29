package com.blog.platform.sso.config;

import com.blog.platform.common.security.Role;
import com.blog.platform.sso.domain.AuthUser;
import com.blog.platform.sso.repository.AuthUserRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-users", havingValue = "true")
public class SeedUsersRunner implements ApplicationRunner {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-password:}")
    private String seedAdminPassword;

    @Value("${app.seed.editor-password:}")
    private String seedEditorPassword;

    @Value("${app.seed.manager-password:}")
    private String seedManagerPassword;

    @Value("${app.seed.purchaser-password:}")
    private String seedPurchaserPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(seedAdminPassword)
                || !StringUtils.hasText(seedEditorPassword)
                || !StringUtils.hasText(seedManagerPassword)
                || !StringUtils.hasText(seedPurchaserPassword)) {
            log.warn(
                    "APP_SEED_USERS=true but SEED_ADMIN_PASSWORD/SEED_EDITOR_PASSWORD/SEED_MANAGER_PASSWORD/SEED_PURCHASER_PASSWORD are missing — seed skipped");
            return;
        }
        upsertSeedUser("admin", "admin@blog.local", seedAdminPassword, Set.of(Role.ADMIN));
        upsertSeedUser("editor", "editor@blog.local", seedEditorPassword, Set.of(Role.EDITOR));
        upsertSeedUser("manager", "manager@blog.local", seedManagerPassword, Set.of(Role.MANAGER));
        upsertSeedUser("purchaser", "purchaser@blog.local", seedPurchaserPassword, Set.of(Role.PURCHASER));
    }

    private void upsertSeedUser(String username, String email, String password, Set<Role> roles) {
        Optional<AuthUser> existing = authUserRepository.findByUsername(username);
        if (existing.isPresent()) {
            log.info("Seed user {} already exists — skipped", username);
            return;
        }
        AuthUser user = new AuthUser();
        user.setRoles(new HashSet<>());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.getRoles().addAll(roles);
        authUserRepository.save(user);
        log.info("Seeded user {}", username);
    }
}
