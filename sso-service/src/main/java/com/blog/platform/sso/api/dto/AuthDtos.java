package com.blog.platform.sso.api.dto;

import com.blog.platform.common.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Email String email,
            @NotBlank
            @Size(min = 10, max = 120)
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                    message = "Password must contain upper, lower and digit"
            )
            String password,
            @NotEmpty Set<Role> roles
    ) {
    }

    public record AuthResponse(String accessToken, String refreshToken, String tokenType) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            Set<Role> roles,
            boolean enabled
    ) {
    }
}
