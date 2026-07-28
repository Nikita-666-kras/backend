package com.blog.platform.admin.client;

import com.blog.platform.admin.api.dto.PartsAdminDtos;
import com.blog.platform.admin.api.dto.PartsAdminDtos.CategoryRequest;
import com.blog.platform.admin.api.dto.PartsAdminDtos.CategoryResponse;
import com.blog.platform.admin.api.dto.PartsAdminDtos.DroneRequest;
import com.blog.platform.admin.api.dto.PartsAdminDtos.DroneResponse;
import com.blog.platform.admin.api.dto.PartsAdminDtos.KitRequest;
import com.blog.platform.admin.api.dto.PartsAdminDtos.KitResponse;
import com.blog.platform.admin.api.dto.PartsAdminDtos.PageResponse;
import com.blog.platform.admin.api.dto.PartsAdminDtos.PartRequest;
import com.blog.platform.admin.api.dto.PartsAdminDtos.PartResponse;
import com.blog.platform.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class PartsServiceClient {

    private final RestClient partsServiceRestClient;

    public PageResponse<PartResponse> searchParts(String q, String status, UUID droneId, UUID categoryId, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/parts")
                .queryParam("page", page)
                .queryParam("size", size);
        if (q != null && !q.isBlank()) builder.queryParam("q", q);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        if (droneId != null) builder.queryParam("droneId", droneId);
        if (categoryId != null) builder.queryParam("categoryId", categoryId);
        return requireData(partsServiceRestClient.get()
                .uri(builder.build(true).toUriString())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<PageResponse<PartResponse>>>() {}));
    }

    public PartResponse getPart(UUID id) {
        return requireData(partsServiceRestClient.get()
                .uri("/parts/by-id/{id}", id)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<PartResponse>>() {}));
    }

    public PartResponse createPart(PartRequest request) {
        return requireData(partsServiceRestClient.post()
                .uri("/parts")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<PartResponse>>() {}));
    }

    public PartResponse updatePart(UUID id, PartRequest request) {
        return requireData(partsServiceRestClient.put()
                .uri("/parts/{id}", id)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<PartResponse>>() {}));
    }

    public PartResponse updatePartStatus(UUID id, String status) {
        return requireData(partsServiceRestClient.patch()
                .uri("/parts/{id}/status", id)
                .body(Map.of("status", status))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<PartResponse>>() {}));
    }

    public void deletePart(UUID id) {
        partsServiceRestClient.delete().uri("/parts/{id}", id).retrieve().toBodilessEntity();
    }

    public PageResponse<KitResponse> searchKits(String q, String status, UUID droneId, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/kits")
                .queryParam("page", page)
                .queryParam("size", size);
        if (q != null && !q.isBlank()) builder.queryParam("q", q);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        if (droneId != null) builder.queryParam("droneId", droneId);
        return requireData(partsServiceRestClient.get()
                .uri(builder.build(true).toUriString())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<PageResponse<KitResponse>>>() {}));
    }

    public KitResponse getKit(UUID id) {
        return requireData(partsServiceRestClient.get()
                .uri("/kits/by-id/{id}", id)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<KitResponse>>() {}));
    }

    public KitResponse createKit(KitRequest request) {
        return requireData(partsServiceRestClient.post()
                .uri("/kits")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<KitResponse>>() {}));
    }

    public KitResponse updateKit(UUID id, KitRequest request) {
        return requireData(partsServiceRestClient.put()
                .uri("/kits/{id}", id)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<KitResponse>>() {}));
    }

    public KitResponse updateKitStatus(UUID id, String status) {
        return requireData(partsServiceRestClient.patch()
                .uri("/kits/{id}/status", id)
                .body(Map.of("status", status))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<KitResponse>>() {}));
    }

    public void deleteKit(UUID id) {
        partsServiceRestClient.delete().uri("/kits/{id}", id).retrieve().toBodilessEntity();
    }

    public PageResponse<DroneResponse> searchDrones(String q, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/drones")
                .queryParam("page", page)
                .queryParam("size", size);
        if (q != null && !q.isBlank()) builder.queryParam("q", q);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        return requireData(partsServiceRestClient.get()
                .uri(builder.build(true).toUriString())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<PageResponse<DroneResponse>>>() {}));
    }

    public DroneResponse getDrone(UUID id) {
        return requireData(partsServiceRestClient.get()
                .uri("/drones/by-id/{id}", id)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneResponse>>() {}));
    }

    public DroneResponse createDrone(DroneRequest request) {
        return requireData(partsServiceRestClient.post()
                .uri("/drones")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneResponse>>() {}));
    }

    public DroneResponse updateDrone(UUID id, DroneRequest request) {
        return requireData(partsServiceRestClient.put()
                .uri("/drones/{id}", id)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneResponse>>() {}));
    }

    public DroneResponse updateDroneStatus(UUID id, String status) {
        return requireData(partsServiceRestClient.patch()
                .uri("/drones/{id}/status", id)
                .body(Map.of("status", status))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneResponse>>() {}));
    }

    public void deleteDrone(UUID id) {
        partsServiceRestClient.delete().uri("/drones/{id}", id).retrieve().toBodilessEntity();
    }

    public List<CategoryResponse> listCategories() {
        return requireData(partsServiceRestClient.get()
                .uri("/part-categories")
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<CategoryResponse>>>() {}));
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        return requireData(partsServiceRestClient.post()
                .uri("/part-categories")
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<CategoryResponse>>() {}));
    }

    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        return requireData(partsServiceRestClient.put()
                .uri("/part-categories/{id}", id)
                .body(request)
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<CategoryResponse>>() {}));
    }

    public void deleteCategory(UUID id) {
        partsServiceRestClient.delete().uri("/part-categories/{id}", id).retrieve().toBodilessEntity();
    }

    public PartsAdminDtos.ImportPreviewResponse previewImport(MultipartFile file) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", toResource(file));
            return requireData(partsServiceRestClient.post()
                    .uri("/parts/import/preview")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<PartsAdminDtos.ImportPreviewResponse>>() {}));
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "Не удалось разобрать файл" : ex.getMessage(), ex);
        }
    }

    public PartsAdminDtos.ImportApplyResponse applyImport(MultipartFile file, PartsAdminDtos.ImportApplyRequest options) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", toResource(file));
            if (options != null) {
                HttpHeaders jsonHeaders = new HttpHeaders();
                jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
                body.add("options", new HttpEntity<>(options, jsonHeaders));
            }
            return requireData(partsServiceRestClient.post()
                    .uri("/parts/import/apply")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<PartsAdminDtos.ImportApplyResponse>>() {}));
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage() == null ? "Не удалось выполнить импорт" : ex.getMessage(), ex);
        }
    }

    public byte[] importTemplate() {
        return partsServiceRestClient.get()
                .uri("/parts/import/template.csv")
                .retrieve()
                .body(byte[].class);
    }

    private ByteArrayResource toResource(MultipartFile file) throws Exception {
        return new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) {
            throw new IllegalArgumentException("Empty response from parts-service");
        }
        return response.data();
    }
}
