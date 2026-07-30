package com.blog.platform.logging.domain;

import java.time.Instant;

public record LogEventRecord(
        String service,
        String category,
        String level,
        String message,
        String detailsJson,
        String actorId,
        String actorUsername,
        String requestId,
        Instant occurredAt,
        String fingerprint
) {
}
