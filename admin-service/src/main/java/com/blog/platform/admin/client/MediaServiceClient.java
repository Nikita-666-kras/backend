package com.blog.platform.admin.client;

import com.blog.platform.admin.api.dto.AdminDtos.MediaPageResponse;
import com.blog.platform.admin.api.dto.AdminDtos.MediaResponse;
import com.blog.platform.common.api.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class MediaServiceClient {

    private final RestClient postServiceRestClient;

    public MediaResponse upload(MultipartFile file, UUID uploadedBy) {
        try {
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            builder.part("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            }).contentType(MediaType.parseMediaType(
                    file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType()
            ));
            builder.part("uploadedBy", uploadedBy.toString());

            MultiValueMap<String, org.springframework.http.HttpEntity<?>> multipart = builder.build();
            ApiResponse<MediaResponse> response = postServiceRestClient.post()
                    .uri("/media")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return requireData(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка загрузки медиа: " + ex.getMessage(), ex);
        }
    }

    public MediaPageResponse list(String kind, String q, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/media")
                .queryParam("page", page)
                .queryParam("size", size);
        if (kind != null && !kind.isBlank()) {
            builder.queryParam("kind", kind);
        }
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q);
        }
        ApiResponse<MediaPageResponse> response = postServiceRestClient.get()
                .uri(builder.build(true).toUriString())
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return requireData(response);
    }

    public void delete(UUID id) {
        postServiceRestClient.delete()
                .uri("/media/{id}", id)
                .retrieve()
                .toBodilessEntity();
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) {
            throw new IllegalArgumentException("Empty response from post-service media");
        }
        return response.data();
    }
}
