package com.blog.platform.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record PostRequest(
            @NotBlank String title,
            @NotBlank String shortDescription,
            @NotBlank String content,
            UUID coverMediaId,
            Set<String> tags,
            Set<String> categories,
            Set<String> mediaObjectNames
    ) {
    }

    public record MediaItem(
            UUID id,
            String url,
            String kind,
            String originalName
    ) {
    }

    public record PostResponse(
            UUID id,
            String title,
            String slug,
            String shortDescription,
            String content,
            String htmlContent,
            String status,
            Integer readingTime,
            Long views,
            Long likes,
            UUID authorId,
            UUID coverMediaId,
            String coverUrl,
            Set<String> mediaObjectNames,
            List<MediaItem> media,
            Set<String> tags,
            Set<String> categories,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PageResponse(
            List<PostResponse> content,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {
    }

    public record DashboardStats(
            long drafts,
            long published,
            long archived,
            long total
    ) {
    }

    public record StatusBody(@NotNull String status) {
    }

    public record BulkRequest(
            @NotNull List<UUID> ids,
            @NotBlank String action
    ) {
    }

    public record BulkResult(
            int success,
            int failed,
            List<String> errors
    ) {
    }

    public record MediaResponse(
            UUID id,
            String originalName,
            String contentType,
            long sizeBytes,
            String kind,
            String url,
            UUID uploadedBy,
            Instant createdAt
    ) {
    }

    public record MediaPageResponse(
            List<MediaResponse> content,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {
    }
}
