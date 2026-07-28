package com.blog.platform.parts.api.dto;

import java.util.List;
import java.util.Map;

public final class ImportDtos {
    private ImportDtos() {
    }

    public enum ImportFormat {
        CSV,
        XLSX,
        GBS_JSON,
        JSON
    }

    public enum TargetField {
        SKIP,
        SKU,
        NAME,
        PRICE,
        DESCRIPTION,
        DRONE,
        CATEGORY,
        KIT_SKU,
        EXTERNAL_ID,
        BARCODE
    }

    public record ColumnMapping(
            String sourceColumn,
            TargetField targetField
    ) {
    }

    public record ImportPreviewResponse(
            ImportFormat format,
            List<String> headers,
            List<ColumnMapping> suggestedMapping,
            List<Map<String, String>> sampleRows,
            int totalRows,
            PreviewStats stats,
            List<RowIssue> issues
    ) {
    }

    public record PreviewStats(
            int valid,
            int toCreate,
            int toUpdate,
            int invalid
    ) {
    }

    public record RowIssue(
            int rowNumber,
            String message
    ) {
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
