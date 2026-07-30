package com.blog.platform.common.logging;

import java.time.Instant;
import java.util.Map;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record LogEvent(
            String service,
            String category,
            String level,
            String message,
            Map<String, Object> details,
            String actorId,
            String actorUsername,
            String requestId,
            Instant occurredAt
    ) {
    }

    public record LogIngestRequest(java.util.List<LogEvent> events) {
    }
}
