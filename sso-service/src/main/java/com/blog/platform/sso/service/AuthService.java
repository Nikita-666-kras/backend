package com.blog.platform.sso.service;

import com.blog.platform.common.exception.ForbiddenException;
import com.blog.platform.common.exception.TooManyRequestsException;
import com.blog.platform.common.exception.UnauthorizedException;
import com.blog.platform.common.logging.AuditClient;
import com.blog.platform.common.security.JwtClaims;
import com.blog.platform.common.security.Role;
import com.blog.platform.sso.api.dto.AuthDtos.AuthResponse;
import com.blog.platform.sso.api.dto.AuthDtos.CreateUserRequest;
import com.blog.platform.sso.api.dto.AuthDtos.LoginRequest;
import com.blog.platform.sso.api.dto.AuthDtos.RefreshRequest;
import com.blog.platform.sso.api.dto.AuthDtos.UserResponse;
import com.blog.platform.sso.domain.AuthUser;
import com.blog.platform.sso.domain.RefreshToken;
import com.blog.platform.sso.repository.AuthUserRepository;
import com.blog.platform.sso.repository.RefreshTokenRepository;
import com.blog.platform.sso.security.JwtProvider;
import com.blog.platform.sso.security.TokenHasher;
import io.jsonwebtoken.Claims;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final TokenHasher tokenHasher;
    private final LoginRateLimitService loginRateLimitService;
    private final AuditClient auditClient;

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        loginRateLimitService.ensureLoginAttemptAllowed(request.username(), ipAddress);
        AuthUser user = authUserRepository.findByUsername(request.username()).orElse(null);
        if (user == null) {
            auditClient.warn("AUTH", "Login failed: unknown user", Map.of("username", request.username(), "ip", ipAddress), null, request.username(), null);
            throw new UnauthorizedException("Invalid credentials");
        }
        try {
            loginRateLimitService.ensureAccountNotLocked(user);
        } catch (TooManyRequestsException ex) {
            auditClient.security("SECURITY", "Blocked login for locked account", Map.of("username", request.username(), "ip", ipAddress), user.getId().toString(), user.getUsername(), null);
            throw ex;
        }
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginRateLimitService.onLoginFailure(user);
            auditClient.warn("AUTH", "Login failed: invalid password", Map.of("username", request.username(), "ip", ipAddress), user.getId().toString(), user.getUsername(), null);
            throw new UnauthorizedException("Invalid credentials");
        }
        loginRateLimitService.onLoginSuccess(user);
        auditClient.audit("AUTH", "Login successful", Map.of("ip", ipAddress), user.getId().toString(), user.getUsername(), null);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request, String ipAddress) {
        loginRateLimitService.ensureRefreshAllowed(ipAddress);
        String tokenHash = tokenHasher.hash(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (stored.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new UnauthorizedException("Refresh token expired");
        }
        AuthUser user = authUserRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!user.isEnabled()) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new UnauthorizedException("User disabled");
        }
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        auditClient.audit("AUTH", "Token refreshed", Map.of("ip", ipAddress), user.getId().toString(), user.getUsername(), null);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken, String accessToken) {
        UUID userId = null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            String tokenHash = tokenHasher.hash(refreshToken);
            RefreshToken stored = refreshTokenRepository.findByTokenAndRevokedFalse(tokenHash).orElse(null);
            if (stored != null) {
                stored.setRevoked(true);
                refreshTokenRepository.save(stored);
                userId = stored.getUserId();
            }
        }
        if (userId == null && accessToken != null && !accessToken.isBlank()) {
            try {
                Claims claims = jwtProvider.parse(accessToken);
                userId = UUID.fromString(claims.get(JwtClaims.USER_ID, String.class));
            } catch (Exception ignored) {
                // ignore invalid access token on logout
            }
        }
        if (userId != null) {
            UUID finalUserId = userId;
            authUserRepository.findById(userId).ifPresent(user -> {
                invalidateAccessTokens(user);
                auditClient.audit("AUTH", "Logout successful", Map.of(), finalUserId.toString(), user.getUsername(), null);
            });
        }
    }

    @Transactional(readOnly = true)
    public UserResponse me(String accessToken) {
        Claims claims = jwtProvider.parse(accessToken);
        UUID userId = UUID.fromString(claims.get(JwtClaims.USER_ID, String.class));
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        ensureTokenVersion(claims, user);
        if (!user.isEnabled()) {
            throw new UnauthorizedException("User disabled");
        }
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Long> activeTokenVersions() {
        Map<UUID, Long> versions = new HashMap<>();
        for (AuthUser user : authUserRepository.findAll()) {
            versions.put(user.getId(), user.getAccessTokenVersion());
        }
        return versions;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (authUserRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (authUserRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        AuthUser user = new AuthUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(new HashSet<>(request.roles()));
        AuthUser saved = authUserRepository.save(user);
        auditClient.audit("USERS", "User created", Map.of("roles", request.roles().toString()), saved.getId().toString(), saved.getUsername(), null);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(String q) {
        String query = q == null ? "" : q.trim();
        return authUserRepository.search(query).stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<Role> roles, UUID actorId) {
        AuthUser user = requireUser(id);
        if (user.getId().equals(actorId) && !roles.contains(Role.ADMIN)) {
            throw new ForbiddenException("������ ����� � ���� ���� ADMIN");
        }
        user.setRoles(new HashSet<>(roles));
        invalidateAccessTokens(user);
        AuthUser saved = authUserRepository.save(user);
        auditClient.audit("USERS", "Roles updated", Map.of("roles", roles.toString(), "actorId", actorId.toString()), saved.getId().toString(), saved.getUsername(), null);
        return toResponse(saved);
    }

    @Transactional
    public UserResponse updateEnabled(UUID id, boolean enabled, UUID actorId) {
        AuthUser user = requireUser(id);
        if (user.getId().equals(actorId) && !enabled) {
            throw new ForbiddenException("������ ��������� ����������� �������");
        }
        user.setEnabled(enabled);
        if (!enabled) {
            for (RefreshToken token : refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId())) {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            }
            invalidateAccessTokens(user);
        }
        AuthUser saved = authUserRepository.save(user);
        auditClient.audit("USERS", enabled ? "User enabled" : "User disabled", Map.of("actorId", actorId.toString()), saved.getId().toString(), saved.getUsername(), null);
        return toResponse(saved);
    }

    @Transactional
    public void deleteUser(UUID id, UUID actorId) {
        AuthUser user = requireUser(id);
        if (user.getId().equals(actorId)) {
            throw new ForbiddenException("������ ������� ����������� �������");
        }
        refreshTokenRepository.deleteAll(refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId()));
        authUserRepository.delete(user);
        auditClient.audit("USERS", "User deleted", Map.of("actorId", actorId.toString()), user.getId().toString(), user.getUsername(), null);
    }

    private AuthUser requireUser(UUID id) {
        return authUserRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private AuthResponse issueTokens(AuthUser user) {
        String access = jwtProvider.createAccessToken(
                user.getId(),
                user.getUsername(),
                user.getRoles(),
                user.getAccessTokenVersion()
        );
        String refresh = tokenHasher.newOpaqueToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(tokenHasher.hash(refresh));
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(jwtProvider.refreshExpirationTime());
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(access, refresh, "Bearer");
    }

    private void invalidateAccessTokens(AuthUser user) {
        user.setAccessTokenVersion(user.getAccessTokenVersion() + 1);
        authUserRepository.save(user);
    }

    private void ensureTokenVersion(Claims claims, AuthUser user) {
        long tokenVersion = extractTokenVersion(claims);
        if (tokenVersion < user.getAccessTokenVersion()) {
            throw new UnauthorizedException("Token revoked");
        }
    }

    private long extractTokenVersion(Claims claims) {
        Object raw = claims.get(JwtClaims.TOKEN_VERSION);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String value && !value.isBlank()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private UserResponse toResponse(AuthUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRoles(), user.isEnabled());
    }
}
