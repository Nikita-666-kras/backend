package com.blog.platform.admin.security;

import com.blog.platform.common.exception.ForbiddenException;
import com.blog.platform.common.exception.UnauthorizedException;
import com.blog.platform.common.security.Role;
import com.blog.platform.common.security.SecurityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {

    public void requireEditorOrAdmin(HttpServletRequest request) {
        Set<Role> roles = roles(request);
        if (!roles.contains(Role.ADMIN) && !roles.contains(Role.EDITOR)) {
            throw new ForbiddenException("EDITOR or ADMIN role required");
        }
    }

    public void requireAdmin(HttpServletRequest request) {
        if (!roles(request).contains(Role.ADMIN)) {
            throw new ForbiddenException("ADMIN role required");
        }
    }

    public UUID userId(HttpServletRequest request) {
        String value = request.getHeader(SecurityHeaders.USER_ID);
        if (value == null || value.isBlank()) {
            throw new UnauthorizedException("Missing user identity");
        }
        return UUID.fromString(value);
    }

    public Set<Role> roles(HttpServletRequest request) {
        String header = request.getHeader(SecurityHeaders.USER_ROLES);
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Role::valueOf)
                .collect(Collectors.toSet());
    }
}
