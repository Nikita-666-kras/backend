package com.blog.platform.common.logging;

import com.blog.platform.common.logging.AuditDtos.LogEvent;
import com.blog.platform.common.logging.AuditDtos.LogIngestRequest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuditClient {

    private final RestClient restClient;
    private final boolean enabled;
    private final String serviceName;

    public AuditClient(
            @Value("${logging.audit.enabled:false}") boolean enabled,
            @Value("${logging.audit.base-url:http://logging-service:9008}") String baseUrl,
            @Value("${logging.audit.internal-api-key:${LOGGING_INTERNAL_API_KEY:${INTERNAL_API_KEY:}}}") String internalApiKey,
            @Value("${spring.application.name:unknown-service}") String serviceName
    ) {
        this.enabled = enabled;
        this.serviceName = serviceName;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Api-Key", internalApiKey)
                .build();
    }

    public void audit(String category, String message, Map<String, Object> details, String actorId, String actorUsername, String requestId) {
        send("AUDIT", category, message, details, actorId, actorUsername, requestId);
    }

    public void warn(String category, String message, Map<String, Object> details, String actorId, String actorUsername, String requestId) {
        send("WARN", category, message, details, actorId, actorUsername, requestId);
    }

    public void error(String category, String message, Map<String, Object> details, String actorId, String actorUsername, String requestId) {
        send("ERROR", category, message, details, actorId, actorUsername, requestId);
    }

    public void security(String category, String message, Map<String, Object> details, String actorId, String actorUsername, String requestId) {
        send("SECURITY", category, message, details, actorId, actorUsername, requestId);
    }

    private void send(
            String level,
            String category,
            String message,
            Map<String, Object> details,
            String actorId,
            String actorUsername,
            String requestId
    ) {
        if (!enabled) {
            return;
        }

        LogEvent event = new LogEvent(
                serviceName,
                category,
                level,
                message,
                details,
                actorId,
                actorUsername,
                requestId,
                Instant.now()
        );

        CompletableFuture.runAsync(() -> {
            try {
                restClient.post()
                        .uri("/internal/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new LogIngestRequest(List.of(event)))
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception ignored) {
                // Logging failures must never break business operations.
            }
        });
    }
}
