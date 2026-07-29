package com.blog.platform.admin.security;

import com.blog.platform.common.exception.ForbiddenException;
import com.blog.platform.common.exception.UnauthorizedException;
import com.blog.platform.common.security.Role;
import com.blog.platform.common.security.SecurityHeaders;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {

    private static final Set<String> EDITOR_MEDIA_SECTIONS = Set.of("ARTICLES", "EDUCATION", "OTHER");
    private static final Set<String> PURCHASER_MEDIA_SECTIONS = Set.of("PARTS", "DRONES", "SERVICE", "OTHER");
    private static final Set<String> MANAGER_MEDIA_SECTIONS = Set.of("OTHER");

    public void requireEditorOrAdmin(HttpServletRequest request) {
        Set<Role> roles = roles(request);
        if (!roles.contains(Role.ADMIN) && !roles.contains(Role.EDITOR)) {
            throw new ForbiddenException("EDITOR or ADMIN role required");
        }
    }

    public void requireCatalogOrAdmin(HttpServletRequest request) {
        Set<Role> roles = roles(request);
        if (!roles.contains(Role.ADMIN) && !roles.contains(Role.PURCHASER)) {
            throw new ForbiddenException("PURCHASER or ADMIN role required");
        }
    }

    public void requireAdminUiAccess(HttpServletRequest request) {
        Set<Role> roles = roles(request);
        if (!roles.contains(Role.ADMIN)
                && !roles.contains(Role.EDITOR)
                && !roles.contains(Role.PURCHASER)
                && !roles.contains(Role.MANAGER)) {
            throw new ForbiddenException("Access denied");
        }
    }

    public void requireAnyMediaAccess(HttpServletRequest request) {
        if (!hasAnyMediaRole(roles(request))) {
            throw new ForbiddenException("Access denied");
        }
    }

    public void requireMediaAccess(HttpServletRequest request, String section) {
        Set<Role> roles = roles(request);
        if (!hasAnyMediaRole(roles)) {
            throw new ForbiddenException("Access denied");
        }
        if (section == null || section.isBlank()) {
            if (!roles.contains(Role.ADMIN)) {
                throw new ForbiddenException("Section required");
            }
            return;
        }
        if (!canAccessMediaSection(roles, section)) {
            throw new ForbiddenException("Section access denied");
        }
    }

    public boolean canAccessMediaSection(Set<Role> roles, String section) {
        if (section == null || section.isBlank()) {
            return roles.contains(Role.ADMIN);
        }
        String normalized = section.trim().toUpperCase(Locale.ROOT);
        if ("OTHER".equals(normalized)) {
            return hasAnyMediaRole(roles);
        }
        if (roles.contains(Role.ADMIN)) {
            return true;
        }
        if (roles.contains(Role.EDITOR) && EDITOR_MEDIA_SECTIONS.contains(normalized)) {
            return true;
        }
        if (roles.contains(Role.PURCHASER) && PURCHASER_MEDIA_SECTIONS.contains(normalized)) {
            return true;
        }
        return roles.contains(Role.MANAGER) && MANAGER_MEDIA_SECTIONS.contains(normalized);
    }

    private boolean hasAnyMediaRole(Set<Role> roles) {
        return roles.contains(Role.ADMIN)
                || roles.contains(Role.EDITOR)
                || roles.contains(Role.MANAGER)
                || roles.contains(Role.PURCHASER);
    }

    public void requirePostOwnerOrAdmin(HttpServletRequest request, UUID authorId) {
        Set<Role> roles = roles(request);
        if (roles.contains(Role.ADMIN)) {
            return;
        }
        if (roles.contains(Role.EDITOR) && userId(request).equals(authorId)) {
            return;
        }
        throw new ForbiddenException("Access denied");
    }

    public boolean isAdmin(HttpServletRequest request) {
        return roles(request).contains(Role.ADMIN);
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
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("Invalid user identity");
        }
    }

    public Set<Role> roles(HttpServletRequest request) {
        String header = request.getHeader(SecurityHeaders.USER_ROLES);
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::toRole)
                .collect(Collectors.toSet());
    }

    private Role toRole(String value) {
        try {
            return Role.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException("Invalid role in request");
        }
    }
}
