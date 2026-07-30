package com.blog.platform.logging.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LoggingDtos {

    private LoggingDtos() {
    }

    public record LogIngestRequest(@NotEmpty List<@Valid LogEventInput> events) {
    }

    public record LogEventInput(
            @NotBlank String service,
            @NotBlank String category,
            @NotBlank String level,
            @NotBlank String message,
            Map<String, Object> details,
            String actorId,
            String actorUsername,
            String requestId,
            Instant occurredAt
    ) {
    }

    public record LogIngestResponse(int accepted, int dropped) {
    }

    public record LogQueryResponse(List<LogEventView> items, long totalElements, int page, int size) {
    }

    public record LogEventView(
            long id,
            Instant createdAt,
            Instant lastSeenAt,
            String service,
            String category,
            String level,
            String message,
            String detailsJson,
            String actorId,
            String actorUsername,
            String requestId,
            long count
    ) {
    }

    public record LogStatsResponse(
            @NotNull Map<String, Long> byLevel,
            @NotNull Map<String, Long> byCategory
    ) {
    }
}
