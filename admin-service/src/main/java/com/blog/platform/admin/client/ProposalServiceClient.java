package com.blog.platform.admin.client;

import com.blog.platform.common.api.ApiResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class ProposalServiceClient {
    private final RestClient proposalServiceRestClient;

    public List<DroneModelResponse> droneModels() {
        return requireData(proposalServiceRestClient.get().uri("/admin/kp/drone-models").retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<DroneModelResponse>>>() {}));
    }

    public DroneModelResponse createDroneModel(DroneModelRequest req) {
        return requireData(proposalServiceRestClient.post().uri("/admin/kp/drone-models").body(req).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneModelResponse>>() {}));
    }

    public DroneModelResponse updateDroneModel(UUID id, DroneModelRequest req) {
        return requireData(proposalServiceRestClient.put().uri("/admin/kp/drone-models/{id}", id).body(req).retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<DroneModelResponse>>() {}));
    }

    public List<ProposalResponse> proposals() {
        return requireData(proposalServiceRestClient.get().uri("/admin/kp/proposals").retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<List<ProposalResponse>>>() {}));
    }

    public byte[] downloadPdf(UUID id) {
        return proposalServiceRestClient.get().uri("/admin/kp/proposals/{id}/pdf", id).retrieve().body(byte[].class);
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) throw new IllegalStateException("Proposal service empty response");
        return response.data();
    }

    public record DroneModelRequest(String code, String name, BigDecimal defaultPrice, Integer sortOrder, Boolean active) {}
    public record DroneModelResponse(UUID id, String code, String name, BigDecimal defaultPrice, Integer sortOrder, boolean active) {}
    public record ProposalResponse(UUID id, Integer number, String managerUsername, String recipient, String droneModelName,
                                   String status, BigDecimal grandTotal, BigDecimal ndsTotal, String pdfPath) {}
}
