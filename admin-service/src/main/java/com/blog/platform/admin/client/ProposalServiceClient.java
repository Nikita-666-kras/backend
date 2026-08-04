package com.blog.platform.admin.client;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.security.Role;
import com.blog.platform.common.security.SecurityHeaders;
import java.math.BigDecimal;
import java.util.Set;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ProposalServiceClient {
    private final RestClient proposalServiceRestClient;

    public List<DroneModelResponse> droneModels(UUID userId, Set<Role> roles) {
        return requireData(proposalServiceRestClient.get()
                .uri("/admin/kp/drone-models")
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<DroneModelResponse>>>() {}));
    }

    public DroneModelResponse createDroneModel(DroneModelRequest req, UUID userId, Set<Role> roles) {
        return requireData(proposalServiceRestClient.post()
                .uri("/admin/kp/drone-models")
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .body(req).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneModelResponse>>() {}));
    }

    public DroneModelResponse updateDroneModel(UUID id, DroneModelRequest req, UUID userId, Set<Role> roles) {
        return requireData(proposalServiceRestClient.put()
                .uri("/admin/kp/drone-models/{id}", id)
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .body(req).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneModelResponse>>() {}));
    }

    public void deleteDroneModel(UUID id, UUID userId, Set<Role> roles) {
        proposalServiceRestClient.delete()
                .uri("/admin/kp/drone-models/{id}", id)
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .retrieve()
                .toBodilessEntity();
    }

    public ZipPackageResponse getZipPackage(UUID droneModelId, UUID userId, Set<Role> roles) {
        return requireData(proposalServiceRestClient.get()
                .uri("/admin/kp/drone-models/{id}/zip-package", droneModelId)
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<ZipPackageResponse>>() {}));
    }

    public ZipPackageResponse saveZipPackage(UUID droneModelId, ZipPackageRequest req, UUID userId, Set<Role> roles) {
        return requireData(proposalServiceRestClient.put()
                .uri("/admin/kp/drone-models/{id}/zip-package", droneModelId)
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .body(req).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<ZipPackageResponse>>() {}));
    }

    public List<ProposalResponse> proposals(UUID userId, Set<Role> roles) {
        return requireData(proposalServiceRestClient.get()
                .uri("/admin/kp/proposals")
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<ProposalResponse>>>() {}));
    }

    public PdfFile downloadPdf(UUID id, UUID userId, Set<Role> roles) {
        var response = proposalServiceRestClient.get()
                .uri("/admin/kp/proposals/{id}/pdf", id)
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .exchange((req, res) -> {
                    byte[] body = res.getBody() == null ? new byte[0] : res.getBody().readAllBytes();
                    String disposition = res.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                    return new PdfFile(body, disposition);
                });
        return response;
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) throw new IllegalStateException("Proposal service empty response");
        return response.data();
    }

    private String rolesHeader(Set<Role> roles) {
        return roles.stream().map(Role::name).collect(Collectors.joining(","));
    }

    public record PdfFile(byte[] bytes, String contentDisposition) {}
    public record DroneModelRequest(String code, String name, BigDecimal defaultPrice, BigDecimal dronePrice,
                                    String vatMode, List<PriceComponentRequest> components,
                                    Integer sortOrder, Boolean active) {}
    public record PriceComponentRequest(String name, BigDecimal unitPrice, Integer qtyPerKit) {}
    public record DroneModelResponse(UUID id, String code, String name, BigDecimal defaultPrice, BigDecimal dronePrice,
                                     String vatMode, List<PriceComponentResponse> components,
                                     Integer sortOrder, boolean active, boolean hasZipPackage) {}
    public record PriceComponentResponse(String name, BigDecimal unitPrice, Integer qtyPerKit) {}
    public record ZipItemRequest(String name, String sku, Integer qty, BigDecimal unitPrice, Integer sortOrder) {}
    public record ZipPackageRequest(String name, BigDecimal price, List<ZipItemRequest> items) {}
    public record ZipItemResponse(UUID id, String name, String sku, Integer qty, BigDecimal unitPrice, Integer sortOrder) {}
    public record ZipPackageResponse(UUID droneModelId, String name, BigDecimal price, List<ZipItemResponse> items) {}
    public record ProposalResponse(UUID id, Integer number, String managerUsername, String recipient, String droneModelName,
                                   String status, BigDecimal grandTotal, BigDecimal ndsTotal, String pdfPath) {}
}
