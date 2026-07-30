package com.blog.platform.admin.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LogsDtos {

    private LogsDtos() {
    }

    public record LogsPageResponse(List<LogEntry> items, long totalElements, int page, int size) {
    }

    public record LogEntry(
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

    public record LogStatsResponse(Map<String, Long> byLevel, Map<String, Long> byCategory) {
    }
}
