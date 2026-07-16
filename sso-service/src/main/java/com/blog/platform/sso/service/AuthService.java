package com.blog.platform.sso.service;

import com.blog.platform.common.exception.UnauthorizedException;
import com.blog.platform.common.security.JwtClaims;
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
import java.util.HashSet;
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

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
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
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        String tokenHash = tokenHasher.hash(refreshToken);
        refreshTokenRepository.findByTokenAndRevokedFalse(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public UserResponse me(String accessToken) {
        Claims claims = jwtProvider.parse(accessToken);
        UUID userId = UUID.fromString(claims.get(JwtClaims.USER_ID, String.class));
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!user.isEnabled()) {
            throw new UnauthorizedException("User disabled");
        }
        return toResponse(user);
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
        return toResponse(authUserRepository.save(user));
    }

    private AuthResponse issueTokens(AuthUser user) {
        String access = jwtProvider.createAccessToken(user.getId(), user.getUsername(), user.getRoles());
        String refresh = tokenHasher.newOpaqueToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(tokenHasher.hash(refresh));
        refreshToken.setUserId(user.getId());
        refreshToken.setExpiresAt(jwtProvider.refreshExpirationTime());
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(access, refresh, "Bearer");
    }

    private UserResponse toResponse(AuthUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRoles(), user.isEnabled());
    }
}
