package com.blog.platform.admin.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PartsAdminDtos {
    private PartsAdminDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 180) String slug,
            UUID parentId,
            Integer sortOrder
    ) {
    }

    public record CategoryResponse(
            UUID id,
            String name,
            String slug,
            UUID parentId,
            Integer sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DroneRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 220) String slug,
            @Size(max = 1000) String description,
            UUID imageMediaId,
            String status,
            Integer sortOrder
    ) {
    }

    public record DroneResponse(
            UUID id,
            String name,
            String slug,
            String description,
            UUID imageMediaId,
            String imageUrl,
            String status,
            Integer sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PartRequest(
            @NotBlank @Size(max = 240) String name,
            @NotBlank @Size(max = 120) String sku,
            String description,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            @Size(max = 3) String currency,
            UUID droneId,
            UUID categoryId,
            UUID coverMediaId,
            List<UUID> mediaIds,
            String status,
            Integer sortOrder,
            String externalSource,
            @Size(max = 120) String externalId
    ) {
    }

    public record PartResponse(
            UUID id,
            String name,
            String sku,
            String description,
            BigDecimal price,
            String currency,
            UUID droneId,
            String droneName,
            UUID categoryId,
            String categoryName,
            UUID coverMediaId,
            String coverUrl,
            List<UUID> mediaIds,
            List<String> mediaUrls,
            String status,
            Integer sortOrder,
            String externalSource,
            String externalId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record KitItemRequest(
            @NotNull UUID partId,
            @NotNull @Min(1) Integer qty
    ) {
    }

    public record KitItemResponse(
            UUID partId,
            String partSku,
            String partName,
            Integer qty,
            BigDecimal partPrice
    ) {
    }

    public record KitRequest(
            @NotBlank @Size(max = 240) String name,
            @NotBlank @Size(max = 120) String sku,
            String description,
            @NotNull @DecimalMin("0.00") BigDecimal price,
            @Size(max = 3) String currency,
            String priceMode,
            UUID droneId,
            UUID coverMediaId,
            List<UUID> mediaIds,
            @Valid List<KitItemRequest> items,
            String status,
            Integer sortOrder
    ) {
    }

    public record KitResponse(
            UUID id,
            String name,
            String sku,
            String description,
            BigDecimal price,
            String currency,
            String priceMode,
            UUID droneId,
            String droneName,
            UUID coverMediaId,
            String coverUrl,
            List<UUID> mediaIds,
            List<String> mediaUrls,
            List<KitItemResponse> items,
            String status,
            Integer sortOrder,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PageResponse<T>(
            List<T> content,
            long totalElements,
            int totalPages,
            int number,
            int size
    ) {
    }

    public record StatusBody(@NotBlank String status) {
    }

    public record ColumnMapping(String sourceColumn, String targetField) {
    }

    public record ImportPreviewResponse(
            String format,
            List<String> headers,
            List<ColumnMapping> suggestedMapping,
            List<Map<String, String>> sampleRows,
            int totalRows,
            PreviewStats stats,
            List<RowIssue> issues
    ) {
    }

    public record PreviewStats(int valid, int toCreate, int toUpdate, int invalid) {
    }

    public record RowIssue(int rowNumber, String message) {
    }

    public record ImportApplyRequest(
            List<ColumnMapping> mapping,
            boolean createMissingDrones,
            boolean createMissingCategories,
            boolean attachToKits,
            String defaultStatus
    ) {
    }

    public record ImportApplyResponse(
            int created,
            int updated,
            int skipped,
            int kitsTouched,
            List<RowIssue> errors
    ) {
    }
}
