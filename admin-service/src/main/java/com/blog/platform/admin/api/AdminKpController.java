package com.blog.platform.admin.api;

import com.blog.platform.admin.client.ProposalServiceClient;
import com.blog.platform.admin.client.ProposalServiceClient.DroneModelRequest;
import com.blog.platform.admin.client.ProposalServiceClient.ZipPackageRequest;
import com.blog.platform.admin.security.AdminAccessGuard;
import com.blog.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/kp")
public class AdminKpController {
    private final ProposalServiceClient proposalServiceClient;
    private final AdminAccessGuard accessGuard;

    @GetMapping("/drone-models")
    public ApiResponse<?> models(HttpServletRequest request) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        return ApiResponse.of(proposalServiceClient.droneModels(userId, roles));
    }

    @PostMapping("/drone-models")
    public ApiResponse<?> createModel(HttpServletRequest request, @RequestBody DroneModelRequest body) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        return ApiResponse.of(proposalServiceClient.createDroneModel(body, userId, roles));
    }

    @PutMapping("/drone-models/{id}")
    public ApiResponse<?> updateModel(HttpServletRequest request, @PathVariable UUID id, @RequestBody DroneModelRequest body) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        return ApiResponse.of(proposalServiceClient.updateDroneModel(id, body, userId, roles));
    }

    @DeleteMapping("/drone-models/{id}")
    public ResponseEntity<Void> deleteModel(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        proposalServiceClient.deleteDroneModel(id, userId, roles);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/drone-models/{id}/zip-package")
    public ApiResponse<?> getZipPackage(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        return ApiResponse.of(proposalServiceClient.getZipPackage(id, userId, roles));
    }

    @PutMapping("/drone-models/{id}/zip-package")
    public ApiResponse<?> saveZipPackage(HttpServletRequest request, @PathVariable UUID id,
                                         @RequestBody ZipPackageRequest body) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        return ApiResponse.of(proposalServiceClient.saveZipPackage(id, body, userId, roles));
    }

    @GetMapping("/proposals")
    public ApiResponse<?> proposals(HttpServletRequest request) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        return ApiResponse.of(proposalServiceClient.proposals(userId, roles));
    }

    @GetMapping("/proposals/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        var userId = accessGuard.userId(request);
        var roles = accessGuard.roles(request);
        byte[] file = proposalServiceClient.downloadPdf(id, userId, roles);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"kp-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file);
    }
}
