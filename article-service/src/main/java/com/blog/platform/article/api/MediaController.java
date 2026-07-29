package com.blog.platform.article.api;

import com.blog.platform.article.api.dto.MediaDtos.BatchProcessRequest;
import com.blog.platform.article.api.dto.MediaDtos.BatchProcessResponse;
import com.blog.platform.article.api.dto.MediaDtos.MediaResponse;
import com.blog.platform.article.api.dto.MediaDtos.PageResponse;
import com.blog.platform.article.api.dto.MediaDtos.ProcessRequest;
import com.blog.platform.article.api.dto.MediaDtos.ProcessingSettingsResponse;
import com.blog.platform.article.api.dto.MediaDtos.SectionUpdateRequest;
import com.blog.platform.article.service.MediaFileService;
import com.blog.platform.article.security.TrustedInternalRequest;
import com.blog.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
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
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaFileService mediaFileService;

    @PostMapping
    public ResponseEntity<ApiResponse<MediaResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadedBy") UUID uploadedBy,
            @RequestParam(required = false) String section
    ) {
        return ResponseEntity.ok(ApiResponse.of(mediaFileService.upload(file, uploadedBy, section)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse>> list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean square,
            @RequestParam(required = false) Boolean watermark,
            @RequestParam(required = false) Boolean incomplete,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                mediaFileService.list(kind, section, q, square, watermark, incomplete, page, size)
        ));
    }

    @GetMapping("/processing-settings")
    public ResponseEntity<ApiResponse<ProcessingSettingsResponse>> processingSettings() {
        return ResponseEntity.ok(ApiResponse.of(mediaFileService.processingSettings()));
    }

    /** @deprecated use /processing-settings */
    @GetMapping("/watermark-settings")
    public ResponseEntity<ApiResponse<ProcessingSettingsResponse>> watermarkSettings() {
        return ResponseEntity.ok(ApiResponse.of(mediaFileService.processingSettings()));
    }

    @GetMapping("/{id}/meta")
    public ResponseEntity<ApiResponse<MediaResponse>> meta(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(mediaFileService.getMeta(id)));
    }

    @PatchMapping("/{id}/section")
    public ResponseEntity<ApiResponse<MediaResponse>> updateSection(
            @PathVariable UUID id,
            @RequestBody SectionUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                mediaFileService.updateSection(id, request.section().name())
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> file(@PathVariable UUID id, HttpServletRequest request) {
        return mediaFileService.stream(id, TrustedInternalRequest.isTrusted(request));
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<ApiResponse<MediaResponse>> process(
            @PathVariable UUID id,
            @RequestBody ProcessRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(
                mediaFileService.process(
                        id,
                        request.square(),
                        request.watermark(),
                        request.convertToWebp(),
                        request.backgroundColor(),
                        request.opacity(),
                        request.bgThreshold()
                )
        ));
    }

    @PostMapping("/process-batch")
    public ResponseEntity<ApiResponse<BatchProcessResponse>> processBatch(@RequestBody BatchProcessRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                mediaFileService.processBatch(
                        request.ids(),
                        request.square(),
                        request.watermark(),
                        request.convertToWebp(),
                        request.backgroundColor(),
                        request.opacity(),
                        request.bgThreshold()
                )
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        mediaFileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
