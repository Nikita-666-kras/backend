package com.blog.platform.article.api.dto;

import com.blog.platform.article.domain.MediaKind;
import com.blog.platform.article.domain.MediaSection;
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
            MediaSection section,
            String url,
            UUID uploadedBy,
            Instant createdAt,
            Instant updatedAt,
            boolean square,
            boolean watermark
    ) {
    }

    public record ProcessRequest(
            boolean square,
            boolean watermark,
            boolean convertToWebp,
            String backgroundColor,
            Float opacity,
            Integer bgThreshold
    ) {
    }

    public record BatchProcessRequest(
            List<UUID> ids,
            boolean square,
            boolean watermark,
            boolean convertToWebp,
            String backgroundColor,
            Float opacity,
            Integer bgThreshold
    ) {
    }

    public record BatchProcessResponse(
            int processed,
            int failed,
            List<String> errors
    ) {
    }

    public record ProcessingSettingsResponse(
            String squareBackground,
            String logoPath,
            float opacity,
            int bgThreshold,
            boolean logoAvailable
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

    public record SectionUpdateRequest(
            MediaSection section
    ) {
    }
}
