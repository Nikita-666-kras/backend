package com.blog.platform.admin.client;

import com.blog.platform.admin.api.dto.AdminDtos.PageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.PostRequest;
import com.blog.platform.admin.api.dto.AdminDtos.PostResponse;
import com.blog.platform.common.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class PostServiceClient {

    private final RestClient postServiceRestClient;
    private final ObjectMapper objectMapper;

    public PostResponse create(PostRequest request, UUID authorId) {
        ApiResponse<PostResponse> response = postServiceRestClient.post()
                .uri("/posts")
                .body(toCreateBody(request, authorId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return requireData(response);
    }

    public PostResponse update(UUID id, PostRequest request, UUID authorId) {
        ApiResponse<PostResponse> response = postServiceRestClient.put()
                .uri("/posts/{id}", id)
                .body(toCreateBody(request, authorId))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return requireData(response);
    }

    public PostResponse getById(UUID id) {
        ApiResponse<PostResponse> response = postServiceRestClient.get()
                .uri("/posts/by-id/{id}", id)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return requireData(response);
    }

    public PageResponse search(String q, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/posts")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParam("sort", "createdAt,desc");
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q);
        }
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }

        ApiResponse<JsonNode> response = postServiceRestClient.get()
                .uri(builder.build(true).toUriString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        JsonNode data = requireData(response);
        List<PostResponse> posts = new ArrayList<>();
        for (JsonNode node : data.get("content")) {
            posts.add(objectMapper.convertValue(node, PostResponse.class));
        }
        return new PageResponse(
                posts,
                data.get("totalElements").asLong(),
                data.get("totalPages").asInt(),
                data.get("number").asInt(),
                data.get("size").asInt()
        );
    }

    public PostResponse updateStatus(UUID id, String status) {
        ApiResponse<PostResponse> response = postServiceRestClient.patch()
                .uri("/posts/{id}/status", id)
                .body(Map.of("status", status))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return requireData(response);
    }

    public void delete(UUID id) {
        postServiceRestClient.delete()
                .uri("/posts/{id}", id)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> toCreateBody(PostRequest request, UUID authorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", request.title());
        body.put("shortDescription", request.shortDescription());
        body.put("content", request.content());
        body.put("authorId", authorId.toString());
        body.put("coverMediaId", request.coverMediaId());
        body.put("tags", request.tags() == null ? List.of() : request.tags());
        body.put("categories", request.categories() == null ? List.of() : request.categories());
        body.put("mediaObjectNames", request.mediaObjectNames() == null ? List.of() : request.mediaObjectNames());
        return body;
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) {
            throw new IllegalArgumentException("Empty response from post-service");
        }
        return response.data();
    }
}
