package com.blog.platform.article.api.dto;

import com.blog.platform.article.domain.ArticleStatus;
import com.blog.platform.article.domain.MediaKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ArticleDto(
        UUID id,
        String title,
        String slug,
        String shortDescription,
        String content,
        String htmlContent,
        ArticleStatus status,
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
    public record MediaItem(
            UUID id,
            String url,
            MediaKind kind,
            String originalName
    ) {
    }

    public record Create(
            @NotBlank String title,
            @NotBlank String shortDescription,
            @NotBlank String content,
            @NotNull UUID authorId,
            UUID coverMediaId,
            Set<String> mediaObjectNames,
            Set<String> tags,
            Set<String> categories
    ) {}

    public record StatusUpdate(@NotNull ArticleStatus status) {}
}
