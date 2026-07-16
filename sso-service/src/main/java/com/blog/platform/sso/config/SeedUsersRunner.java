package com.blog.platform.sso.config;

import com.blog.platform.common.security.Role;
import com.blog.platform.sso.domain.AuthUser;
import com.blog.platform.sso.repository.AuthUserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed-users", havingValue = "true")
public class SeedUsersRunner implements ApplicationRunner {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing("admin", "admin@blog.local", "Admin123!", Set.of(Role.ADMIN));
        createIfMissing("editor", "editor@blog.local", "Editor123!", Set.of(Role.EDITOR));
    }

    private void createIfMissing(String username, String email, String password, Set<Role> roles) {
        if (authUserRepository.existsByUsername(username)) {
            return;
        }
        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setRoles(roles);
        authUserRepository.save(user);
        log.info("Seeded user {}", username);
    }
}
