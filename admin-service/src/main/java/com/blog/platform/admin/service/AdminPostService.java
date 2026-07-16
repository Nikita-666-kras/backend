package com.blog.platform.admin.service;

import com.blog.platform.admin.api.dto.AdminDtos.DashboardStats;
import com.blog.platform.admin.api.dto.AdminDtos.PageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.PostRequest;
import com.blog.platform.admin.api.dto.AdminDtos.PostResponse;
import com.blog.platform.admin.client.PostServiceClient;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminPostService {

    private final PostServiceClient postServiceClient;

    public PageResponse list(String q, String status, int page, int size) {
        return postServiceClient.search(q, status, page, size);
    }

    public PostResponse get(UUID id) {
        return postServiceClient.getById(id);
    }

    public PostResponse create(PostRequest request, UUID authorId) {
        return postServiceClient.create(request, authorId);
    }

    public PostResponse update(UUID id, PostRequest request, UUID authorId) {
        return postServiceClient.update(id, request, authorId);
    }

    public PostResponse publish(UUID id) {
        return postServiceClient.updateStatus(id, "PUBLISHED");
    }

    public PostResponse archive(UUID id) {
        return postServiceClient.updateStatus(id, "ARCHIVED");
    }

    public void delete(UUID id) {
        postServiceClient.delete(id);
    }

    public DashboardStats dashboard() {
        long drafts = postServiceClient.search(null, "DRAFT", 0, 1).totalElements();
        long published = postServiceClient.search(null, "PUBLISHED", 0, 1).totalElements();
        long archived = postServiceClient.search(null, "ARCHIVED", 0, 1).totalElements();
        return new DashboardStats(drafts, published, archived, drafts + published + archived);
    }
}
