package com.blog.platform.admin.api;

import com.blog.platform.admin.api.dto.AdminDtos.DashboardStats;
import com.blog.platform.admin.api.dto.AdminDtos.PageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.PostRequest;
import com.blog.platform.admin.api.dto.AdminDtos.PostResponse;
import com.blog.platform.admin.security.AdminAccessGuard;
import com.blog.platform.admin.service.AdminPostService;
import com.blog.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminPostService adminPostService;
    private final AdminAccessGuard accessGuard;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> dashboard(HttpServletRequest request) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.dashboard()));
    }

    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<PageResponse>> list(
            HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.list(q, status, page, size)));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> get(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.get(id)));
    }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<PostResponse>> create(
            HttpServletRequest request,
            @Valid @RequestBody PostRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        UUID authorId = accessGuard.userId(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.create(body, authorId)));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> update(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody PostRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        UUID authorId = accessGuard.userId(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.update(id, body, authorId)));
    }

    @PostMapping("/posts/{id}/publish")
    public ResponseEntity<ApiResponse<PostResponse>> publish(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.publish(id)));
    }

    @PostMapping("/posts/{id}/archive")
    public ResponseEntity<ApiResponse<PostResponse>> archive(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.archive(id)));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        adminPostService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
