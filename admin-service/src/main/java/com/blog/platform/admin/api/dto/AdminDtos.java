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
            Set<String> tags,
            Set<String> categories,
            Set<String> mediaObjectNames
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
            Set<String> mediaObjectNames,
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
}
