package com.blog.platform.article.api;

import com.blog.platform.article.api.dto.MediaDtos.MediaResponse;
import com.blog.platform.article.api.dto.MediaDtos.PageResponse;
import com.blog.platform.article.service.MediaFileService;
import com.blog.platform.common.api.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
            @RequestParam("uploadedBy") UUID uploadedBy
    ) {
        return ResponseEntity.ok(ApiResponse.of(mediaFileService.upload(file, uploadedBy)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse>> list(
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(mediaFileService.list(kind, q, page, size)));
    }

    @GetMapping("/{id}/meta")
    public ResponseEntity<ApiResponse<MediaResponse>> meta(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(mediaFileService.getMeta(id)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> file(@PathVariable UUID id) {
        return mediaFileService.stream(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        mediaFileService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
