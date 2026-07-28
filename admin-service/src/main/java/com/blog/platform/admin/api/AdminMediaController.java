package com.blog.platform.admin.api;

import com.blog.platform.admin.api.dto.AdminDtos.MediaBatchProcessRequest;
import com.blog.platform.admin.api.dto.AdminDtos.MediaBatchProcessResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaPageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaProcessRequest;
import com.blog.platform.admin.api.dto.AdminDtos.MediaResponse;
import com.blog.platform.admin.api.dto.AdminDtos.ProcessingSettingsResponse;
import com.blog.platform.admin.client.PostServiceClient;
import com.blog.platform.admin.security.AdminAccessGuard;
import com.blog.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PostMapping
    public ResponseEntity<ApiResponse<MediaResponse>> upload(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String section
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(postServiceClient.uploadMedia(file, accessGuard.userId(request), section)));
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
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(
                postServiceClient.listMedia(kind, section, q, square, watermark, incomplete, page, size)
        ));
    }

    @GetMapping("/processing-settings")
    public ResponseEntity<ApiResponse<ProcessingSettingsResponse>> processingSettings(HttpServletRequest request) {
        accessGuard.requireEditorOrAdmin(request);
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
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(postServiceClient.processMedia(id, body)));
    }

    @PostMapping("/process-batch")
    public ResponseEntity<ApiResponse<MediaBatchProcessResponse>> processBatch(
            HttpServletRequest request,
            @RequestBody MediaBatchProcessRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(postServiceClient.processMediaBatch(body)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        postServiceClient.deleteMedia(id);
        return ResponseEntity.noContent().build();
    }
}
