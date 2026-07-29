package com.blog.platform.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TokenVersionCache {

    private static final Logger log = LoggerFactory.getLogger(TokenVersionCache.class);
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Map<UUID, Long>> versions = new AtomicReference<>(Map.of());

    public TokenVersionCache(
            @Value("${SSO_SERVICE_URL:http://sso-service:9001}") String ssoServiceUrl,
            @Value("${security.internal-api-keys.sso:${SSO_INTERNAL_API_KEY:${INTERNAL_API_KEY}}}") String ssoInternalApiKey,
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(ssoServiceUrl)
                .defaultHeader(INTERNAL_API_KEY_HEADER, ssoInternalApiKey)
                .build();
    }

    @PostConstruct
    void init() {
        refresh();
    }

    @Scheduled(fixedDelayString = "${security.token-version.refresh-ms:5000}")
    public void refresh() {
        try {
            String body = webClient.get()
                    .uri("/internal/auth/token-versions")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(3));
            if (body == null || body.isBlank()) {
                return;
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.get("data");
            if (data == null || !data.isObject()) {
                return;
            }
            Map<UUID, Long> parsed = new HashMap<>();
            data.fields().forEachRemaining(entry -> {
                try {
                    parsed.put(UUID.fromString(entry.getKey()), entry.getValue().asLong());
                } catch (Exception ignored) {
                    // skip invalid entries
                }
            });
            versions.set(Map.copyOf(parsed));
        } catch (Exception ex) {
            log.debug("Failed to refresh token versions: {}", ex.getMessage());
        }
    }

    public boolean isRevoked(UUID userId, long tokenVersion) {
        Long current = versions.get().get(userId);
        if (current == null) {
            return false;
        }
        return tokenVersion < current;
    }

    public Map<UUID, Long> snapshot() {
        return Collections.unmodifiableMap(versions.get());
    }
}
