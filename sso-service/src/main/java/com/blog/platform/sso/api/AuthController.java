package com.blog.platform.sso.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.exception.ForbiddenException;
import com.blog.platform.common.exception.UnauthorizedException;
import com.blog.platform.common.security.Role;
import com.blog.platform.common.security.SecurityHeaders;
import com.blog.platform.sso.api.dto.AuthDtos.AuthResponse;
import com.blog.platform.sso.api.dto.AuthDtos.CreateUserRequest;
import com.blog.platform.sso.api.dto.AuthDtos.LoginRequest;
import com.blog.platform.sso.api.dto.AuthDtos.RefreshRequest;
import com.blog.platform.sso.api.dto.AuthDtos.UserResponse;
import com.blog.platform.sso.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.of(authService.refresh(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(HttpServletRequest request) {
        String token = extractBearer(request);
        return ResponseEntity.ok(ApiResponse.of(authService.me(token)));
    }

    @PostMapping("/admin/users")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            HttpServletRequest request,
            @Valid @RequestBody CreateUserRequest body
    ) {
        requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(authService.createUser(body)));
    }

    private void requireAdmin(HttpServletRequest request) {
        String rolesHeader = request.getHeader(SecurityHeaders.USER_ROLES);
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new ForbiddenException("Admin role required");
        }
        Set<String> roles = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (!roles.contains(Role.ADMIN.name())) {
            throw new ForbiddenException("Admin role required");
        }
    }

    private String extractBearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("Missing bearer token");
        }
        return header.substring(7);
    }
}
