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

    public List<ProposalResponse> proposals(UUID userId, Set<Role> roles) {
        return requireData(proposalServiceRestClient.get()
                .uri("/admin/kp/proposals")
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<ProposalResponse>>>() {}));
    }

    public byte[] downloadPdf(UUID id, UUID userId, Set<Role> roles) {
        return proposalServiceRestClient.get()
                .uri("/admin/kp/proposals/{id}/pdf", id)
                .header(SecurityHeaders.USER_ID, userId.toString())
                .header(SecurityHeaders.USER_ROLES, rolesHeader(roles))
                .retrieve().body(byte[].class);
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) throw new IllegalStateException("Proposal service empty response");
        return response.data();
    }

    private String rolesHeader(Set<Role> roles) {
        return roles.stream().map(Role::name).collect(Collectors.joining(","));
    }

    public record DroneModelRequest(String code, String name, BigDecimal defaultPrice, Integer sortOrder, Boolean active) {}
    public record DroneModelResponse(UUID id, String code, String name, BigDecimal defaultPrice, Integer sortOrder, boolean active) {}
    public record ProposalResponse(UUID id, Integer number, String managerUsername, String recipient, String droneModelName,
                                   String status, BigDecimal grandTotal, BigDecimal ndsTotal, String pdfPath) {}
}
