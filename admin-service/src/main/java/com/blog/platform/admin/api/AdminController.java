package com.blog.platform.admin.api;

import com.blog.platform.admin.api.dto.AdminDtos.BulkRequest;
import com.blog.platform.admin.api.dto.AdminDtos.BulkResult;
import com.blog.platform.admin.api.dto.AdminDtos.DashboardStats;
import com.blog.platform.admin.api.dto.AdminDtos.PageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.PostRequest;
import com.blog.platform.admin.api.dto.AdminDtos.PostResponse;
import com.blog.platform.admin.security.AdminAccessGuard;
import com.blog.platform.admin.service.AdminPostService;
import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.logging.AuditClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
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
    private final AuditClient auditClient;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> dashboard(HttpServletRequest request) {
        accessGuard.requireAdminUiAccess(request);
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
        return ResponseEntity.ok(ApiResponse.of(adminPostService.list(request, q, status, page, size)));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> get(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(adminPostService.get(request, id)));
    }

    @PostMapping("/posts")
    public ResponseEntity<ApiResponse<PostResponse>> create(
            HttpServletRequest request,
            @Valid @RequestBody PostRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        UUID authorId = accessGuard.userId(request);
        PostResponse created = adminPostService.create(body, authorId);
        auditClient.audit("CONTENT", "Post created", Map.of("postId", created.id().toString()), authorId.toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(created));
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> update(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody PostRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        UUID authorId = accessGuard.userId(request);
        PostResponse updated = adminPostService.update(request, id, body, authorId);
        auditClient.audit("CONTENT", "Post updated", Map.of("postId", id.toString()), authorId.toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(updated));
    }

    @PostMapping("/posts/{id}/publish")
    public ResponseEntity<ApiResponse<PostResponse>> publish(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        UUID actor = accessGuard.userId(request);
        PostResponse published = adminPostService.publish(request, id);
        auditClient.audit("CONTENT", "Post published", Map.of("postId", id.toString()), actor.toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(published));
    }

    @PostMapping("/posts/{id}/archive")
    public ResponseEntity<ApiResponse<PostResponse>> archive(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        UUID actor = accessGuard.userId(request);
        PostResponse archived = adminPostService.archive(request, id);
        auditClient.audit("CONTENT", "Post archived", Map.of("postId", id.toString()), actor.toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(archived));
    }

    @PostMapping("/posts/bulk")
    public ResponseEntity<ApiResponse<BulkResult>> bulk(
            HttpServletRequest request,
            @Valid @RequestBody BulkRequest body
    ) {
        String action = body.action().trim().toUpperCase();
        if ("DELETE".equals(action)) {
            accessGuard.requireAdmin(request);
        } else {
            accessGuard.requireEditorOrAdmin(request);
        }
        BulkResult result = adminPostService.bulk(request, body);
        auditClient.audit("CONTENT", "Post bulk action", Map.of("action", action, "success", result.success(), "failed", result.failed()), accessGuard.userId(request).toString(), null, null);
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        adminPostService.delete(id);
        auditClient.audit("CONTENT", "Post deleted", Map.of("postId", id.toString()), accessGuard.userId(request).toString(), null, null);
        return ResponseEntity.noContent().build();
    }
}
