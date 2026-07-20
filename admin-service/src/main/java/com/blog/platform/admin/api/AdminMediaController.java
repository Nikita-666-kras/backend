package com.blog.platform.admin.api;

import com.blog.platform.admin.api.dto.AdminDtos.MediaPageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaResponse;
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
            @RequestParam("file") MultipartFile file
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(postServiceClient.uploadMedia(file, accessGuard.userId(request))));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MediaPageResponse>> list(
            HttpServletRequest request,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(postServiceClient.listMedia(kind, q, page, size)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        postServiceClient.deleteMedia(id);
        return ResponseEntity.noContent().build();
    }
}
