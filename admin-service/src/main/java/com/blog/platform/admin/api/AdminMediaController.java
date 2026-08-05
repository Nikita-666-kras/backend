package com.blog.platform.admin.api;

import com.blog.platform.admin.api.dto.AdminDtos.MediaBatchProcessRequest;
import com.blog.platform.admin.api.dto.AdminDtos.MediaBatchProcessResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaBatchUploadResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaPageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaProcessRequest;
import com.blog.platform.admin.api.dto.AdminDtos.MediaResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaSectionUpdateRequest;
import com.blog.platform.admin.api.dto.AdminDtos.ProcessingSettingsResponse;
import com.blog.platform.admin.client.PostServiceClient;
import com.blog.platform.admin.security.AdminAccessGuard;
import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.logging.AuditClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/media")
@RequiredArgsConstructor
public class AdminMediaController {

    private final PostServiceClient postServiceClient;
    private final AdminAccessGuard accessGuard;
    private final AuditClient auditClient;

    @PostMapping
    public ResponseEntity<ApiResponse<MediaResponse>> upload(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String section
    ) {
        String effectiveSection = section == null || section.isBlank() ? "OTHER" : section;
        accessGuard.requireMediaAccess(request, effectiveSection);
        MediaResponse response = postServiceClient.uploadMedia(file, accessGuard.userId(request), effectiveSection);
        auditClient.audit("MEDIA", "Media uploaded", Map.of("mediaId", response.id().toString(), "section", effectiveSection), accessGuard.userId(request).toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<MediaBatchUploadResponse>> uploadBatch(
            HttpServletRequest request,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) String section
    ) {
        String effectiveSection = section == null || section.isBlank() ? "OTHER" : section;
        accessGuard.requireMediaAccess(request, effectiveSection);
        MediaBatchUploadResponse response = postServiceClient.uploadMediaBatch(
                files, accessGuard.userId(request), effectiveSection);
        auditClient.audit(
                "MEDIA",
                "Media batch uploaded",
                Map.of("count", response.uploaded().size(), "failed", response.failed(), "section", effectiveSection),
                accessGuard.userId(request).toString(),
                null,
                null);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MediaPageResponse>> list(
            HttpServletRequest request,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean square,
            @RequestParam(required = false) Boolean watermark,
            @RequestParam(required = false) Boolean incomplete,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        accessGuard.requireMediaAccess(request, section);
        return ResponseEntity.ok(ApiResponse.of(
                postServiceClient.listMedia(kind, section, q, square, watermark, incomplete, page, size)
        ));
    }

    @GetMapping("/processing-settings")
    public ResponseEntity<ApiResponse<ProcessingSettingsResponse>> processingSettings(HttpServletRequest request) {
        accessGuard.requireAnyMediaAccess(request);
        return ResponseEntity.ok(ApiResponse.of(postServiceClient.processingSettings()));
    }

    @GetMapping("/watermark-settings")
    public ResponseEntity<ApiResponse<ProcessingSettingsResponse>> watermarkSettings(HttpServletRequest request) {
        return processingSettings(request);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<ApiResponse<MediaResponse>> process(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody MediaProcessRequest body
    ) {
        MediaResponse meta = postServiceClient.getMediaMeta(id);
        accessGuard.requireMediaAccess(request, meta.section());
        MediaResponse response = postServiceClient.processMedia(id, body);
        auditClient.audit("MEDIA", "Media processed", Map.of("mediaId", id.toString(), "section", meta.section()), accessGuard.userId(request).toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/process-batch")
    public ResponseEntity<ApiResponse<MediaBatchProcessResponse>> processBatch(
            HttpServletRequest request,
            @RequestBody MediaBatchProcessRequest body
    ) {
        accessGuard.requireAnyMediaAccess(request);
        if (body.ids() != null) {
            for (UUID id : body.ids()) {
                MediaResponse meta = postServiceClient.getMediaMeta(id);
                accessGuard.requireMediaAccess(request, meta.section());
            }
        }
        MediaBatchProcessResponse response = postServiceClient.processMediaBatch(body);
        auditClient.audit("MEDIA", "Media batch processed", Map.of("ids", body.ids() == null ? 0 : body.ids().size()), accessGuard.userId(request).toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PatchMapping("/{id}/section")
    public ResponseEntity<ApiResponse<MediaResponse>> updateSection(
            HttpServletRequest request,
            @PathVariable UUID id,
            @RequestBody MediaSectionUpdateRequest body
    ) {
        accessGuard.requireAdmin(request);
        if (body.section() == null || body.section().isBlank()) {
            throw new IllegalArgumentException("Section is required");
        }
        MediaResponse response = postServiceClient.updateMediaSection(id, body.section());
        auditClient.audit("MEDIA", "Media section updated", Map.of("mediaId", id.toString(), "section", body.section()), accessGuard.userId(request).toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        postServiceClient.deleteMedia(id);
        auditClient.audit("MEDIA", "Media deleted", Map.of("mediaId", id.toString()), accessGuard.userId(request).toString(), null, null);
        return ResponseEntity.noContent().build();
    }
}
