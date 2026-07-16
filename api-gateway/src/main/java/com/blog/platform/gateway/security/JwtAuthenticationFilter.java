package com.blog.platform.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLES = "roles";

    private static final Set<String> EDITOR_ROLES = Set.of("ADMIN", "EDITOR");

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        stripIdentityHeaders(requestBuilder);

        if (isBlockedPublic(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        if (HttpMethod.OPTIONS.equals(method) || isAnonymousAuth(path) || isPublicPublishedPosts(path, method)) {
            ServerWebExchange next = exchange.mutate().request(requestBuilder.build()).build();
            if (isPublicPublishedPosts(path, method)) {
                next = forcePublishedStatus(next);
            }
            return chain.filter(next);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            String userId = claims.get(CLAIM_USER_ID, String.class);
            String username = claims.getSubject();
            String roles = extractRoles(claims);
            Set<String> roleSet = splitRoles(roles);

            if (requiresEditorRole(path, method) && roleSet.stream().noneMatch(EDITOR_ROLES::contains)) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            ServerHttpRequest mutated = requestBuilder
                    .header(HEADER_USER_ID, userId == null ? "" : userId)
                    .header(HEADER_USERNAME, username == null ? "" : username)
                    .header(HEADER_USER_ROLES, roles)
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private void stripIdentityHeaders(ServerHttpRequest.Builder builder) {
        builder.headers(headers -> {
            headers.remove(HEADER_USER_ID);
            headers.remove(HEADER_USER_ROLES);
            headers.remove(HEADER_USERNAME);
        });
    }

    private boolean isBlockedPublic(String path) {
        if ("/application/health".equals(path) || "/application/health/".equals(path)) {
            return false;
        }
        return path.startsWith("/application/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private boolean isAnonymousAuth(String path) {
        return path.startsWith("/auth/login")
                || path.startsWith("/auth/refresh")
                || path.startsWith("/auth/logout")
                || "/application/health".equals(path)
                || "/application/health/".equals(path);
    }

    private boolean isPublicPublishedPosts(String path, HttpMethod method) {
        if (!HttpMethod.GET.equals(method) || !path.startsWith("/posts")) {
            return false;
        }
        return !path.startsWith("/posts/by-id/");
    }

    private boolean requiresEditorRole(String path, HttpMethod method) {
        if (path.startsWith("/admin/")) {
            return true;
        }
        if (path.startsWith("/posts") && !HttpMethod.GET.equals(method)) {
            return true;
        }
        return path.startsWith("/auth/admin/");
    }

    private ServerWebExchange forcePublishedStatus(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        if (!"/posts".equals(path) && !path.equals("/posts/")) {
            return exchange;
        }
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>(exchange.getRequest().getQueryParams());
        params.put("status", List.of("PUBLISHED"));
        var uri = UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
                .replaceQueryParams(params)
                .build(true)
                .toUri();
        ServerHttpRequest request = exchange.getRequest().mutate().uri(uri).build();
        return exchange.mutate().request(request).build();
    }

    private String extractRoles(Claims claims) {
        Object raw = claims.get(CLAIM_ROLES);
        if (raw instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.joining(","));
        }
        return "";
    }

    private Set<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
