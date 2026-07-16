package com.blog.platform.article.api.dto;

import com.blog.platform.article.domain.MediaKind;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MediaDtos {
    private MediaDtos() {
    }

    public record MediaResponse(
            UUID id,
            String originalName,
            String contentType,
            long sizeBytes,
            MediaKind kind,
            String url,
            UUID uploadedBy,
            Instant createdAt
    ) {
    }

    public record PageResponse(
            List<MediaResponse> content,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {
    }
}
