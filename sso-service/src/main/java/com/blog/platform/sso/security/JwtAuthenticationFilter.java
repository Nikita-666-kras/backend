package com.blog.platform.sso.security;

import com.blog.platform.common.security.JwtClaims;
import com.blog.platform.common.security.SecurityHeaders;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/auth/login")
                || path.startsWith("/auth/refresh")
                || path.startsWith("/auth/logout")
                || path.startsWith("/application/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            Claims claims = jwtProvider.parse(authHeader.substring(7));
            String userId = claims.get(JwtClaims.USER_ID, String.class);
            String username = claims.getSubject();
            String roles = extractRoles(claims);
            HeaderOverrideRequest wrapped = new HeaderOverrideRequest(request);
            wrapped.putHeader(SecurityHeaders.USER_ID, userId == null ? "" : userId);
            wrapped.putHeader(SecurityHeaders.USERNAME, username == null ? "" : username);
            wrapped.putHeader(SecurityHeaders.USER_ROLES, roles);
            filterChain.doFilter(wrapped, response);
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private String extractRoles(Claims claims) {
        Object raw = claims.get(JwtClaims.ROLES);
        if (raw instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.joining(","));
        }
        return "";
    }

    private static final class HeaderOverrideRequest extends HttpServletRequestWrapper {
        private final java.util.Map<String, String> overrides = new java.util.HashMap<>();

        private HeaderOverrideRequest(HttpServletRequest request) {
            super(request);
        }

        void putHeader(String name, String value) {
            overrides.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            if (overrides.containsKey(name)) {
                return overrides.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (overrides.containsKey(name)) {
                return Collections.enumeration(List.of(overrides.get(name)));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new HashSet<>(Collections.list(super.getHeaderNames()));
            names.addAll(overrides.keySet());
            return Collections.enumeration(names);
        }
    }
}
