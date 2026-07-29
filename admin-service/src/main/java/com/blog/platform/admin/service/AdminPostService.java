package com.blog.platform.admin.service;

import com.blog.platform.admin.api.dto.AdminDtos.BulkRequest;
import com.blog.platform.admin.api.dto.AdminDtos.BulkResult;
import com.blog.platform.admin.api.dto.AdminDtos.DashboardStats;
import com.blog.platform.admin.api.dto.AdminDtos.MediaCounts;
import com.blog.platform.admin.api.dto.AdminDtos.PageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.PostRequest;
import com.blog.platform.admin.api.dto.AdminDtos.PostResponse;
import com.blog.platform.admin.api.dto.AdminDtos.StatusCounts;
import com.blog.platform.admin.client.PartsServiceClient;
import com.blog.platform.admin.client.PostServiceClient;
import com.blog.platform.admin.security.AdminAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPostService {

    private final PostServiceClient postServiceClient;
    private final PartsServiceClient partsServiceClient;
    private final AdminAccessGuard accessGuard;

    public PageResponse list(HttpServletRequest request, String q, String status, int page, int size) {
        UUID authorFilter = accessGuard.isAdmin(request) ? null : accessGuard.userId(request);
        return postServiceClient.search(q, status, authorFilter, page, size);
    }

    public PostResponse get(HttpServletRequest request, UUID id) {
        PostResponse post = postServiceClient.getById(id);
        accessGuard.requirePostOwnerOrAdmin(request, post.authorId());
        return post;
    }

    public PostResponse create(PostRequest request, UUID authorId) {
        return postServiceClient.create(request, authorId);
    }

    public PostResponse update(HttpServletRequest request, UUID id, PostRequest body, UUID authorId) {
        PostResponse existing = postServiceClient.getById(id);
        accessGuard.requirePostOwnerOrAdmin(request, existing.authorId());
        return postServiceClient.update(id, body, authorId);
    }

    public PostResponse publish(HttpServletRequest request, UUID id) {
        PostResponse existing = postServiceClient.getById(id);
        accessGuard.requirePostOwnerOrAdmin(request, existing.authorId());
        return postServiceClient.updateStatus(id, "PUBLISHED");
    }

    public PostResponse archive(HttpServletRequest request, UUID id) {
        PostResponse existing = postServiceClient.getById(id);
        accessGuard.requirePostOwnerOrAdmin(request, existing.authorId());
        return postServiceClient.updateStatus(id, "ARCHIVED");
    }

    public void delete(UUID id) {
        postServiceClient.delete(id);
    }

    public BulkResult bulk(HttpServletRequest request, BulkRequest body) {
        String action = body.action().trim().toUpperCase(Locale.ROOT);
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (UUID id : body.ids()) {
            try {
                switch (action) {
                    case "PUBLISH" -> publish(request, id);
                    case "ARCHIVE" -> archive(request, id);
                    case "DELETE" -> delete(id);
                    default -> throw new IllegalArgumentException("Неизвестное действие: " + action);
                }
                success++;
            } catch (Exception ex) {
                errors.add(id + ": " + ex.getMessage());
            }
        }
        return new BulkResult(success, errors.size(), errors);
    }

    public DashboardStats dashboard() {
        StatusCounts posts = statusCounts((status, size) -> postServiceClient.search(null, status, null, 0, size).totalElements());
        StatusCounts parts = statusCounts((status, size) ->
                partsServiceClient.searchParts(null, status, null, null, 0, size).totalElements());
        StatusCounts kits = statusCounts((status, size) ->
                partsServiceClient.searchKits(null, status, null, 0, size).totalElements());
        long mediaTotal = postServiceClient.listMedia(null, null, null, null, null, null, 0, 1).totalElements();
        long mediaIncomplete = postServiceClient.listMedia(null, null, null, null, null, true, 0, 1).totalElements();
        return new DashboardStats(
                posts.drafts(),
                posts.published(),
                posts.archived(),
                posts.total(),
                posts,
                parts,
                kits,
                new MediaCounts(mediaTotal, mediaIncomplete)
        );
    }

    private StatusCounts statusCounts(BiFunction<String, Integer, Long> counter) {
        long drafts = counter.apply("DRAFT", 1);
        long published = counter.apply("PUBLISHED", 1);
        long archived = counter.apply("ARCHIVED", 1);
        return new StatusCounts(drafts, published, archived, drafts + published + archived);
    }
}
