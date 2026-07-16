package com.blog.platform.sso.security;

import com.blog.platform.common.security.JwtClaims;
import com.blog.platform.common.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.access-expiration-seconds:900}")
    private long accessExpiration;

    @Value("${security.jwt.refresh-expiration-seconds:2592000}")
    private long refreshExpiration;

    public String createAccessToken(UUID userId, String username, Collection<Role> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(JwtClaims.USER_ID, userId.toString())
                .claim(JwtClaims.ROLES, roles.stream().map(Enum::name).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessExpiration)))
                .signWith(secretKey())
                .compact();
    }

    public String createRefreshToken(UUID userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(JwtClaims.USER_ID, userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshExpiration)))
                .signWith(secretKey())
                .compact();
    }

    public Instant refreshExpirationTime() {
        return Instant.now().plusSeconds(refreshExpiration);
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public Set<Role> extractRoles(Claims claims) {
        Object raw = claims.get(JwtClaims.ROLES);
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .map(Role::valueOf)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
