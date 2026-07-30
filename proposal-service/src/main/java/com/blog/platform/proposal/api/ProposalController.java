package com.blog.platform.proposal.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.proposal.api.dto.KpDtos;
import com.blog.platform.proposal.client.PartsCatalogClient;
import com.blog.platform.proposal.security.AccessGuard;
import com.blog.platform.proposal.service.KitPresetService;
import com.blog.platform.proposal.service.ProposalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProposalController {
    private final ProposalService service;
    private final PartsCatalogClient partsClient;
    private final KitPresetService kitPresetService;
    private final AccessGuard guard;

    @GetMapping("/manager/kp/drone-models")
    public ApiResponse<List<KpDtos.DroneModelDto>> managerModels(HttpServletRequest request) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(service.listModels(true));
    }

    @GetMapping("/admin/kp/drone-models")
    public ApiResponse<List<KpDtos.DroneModelDto>> adminModels(HttpServletRequest request) {
        guard.requireAdmin(request);
        return ApiResponse.of(service.listModels(false));
    }

    @PostMapping("/admin/kp/drone-models")
    public ApiResponse<KpDtos.DroneModelDto> createModel(HttpServletRequest request, @RequestBody @Valid KpDtos.DroneModelUpsertRequest body) {
        guard.requireAdmin(request);
        return ApiResponse.of(service.upsertModel(null, body));
    }

    @PutMapping("/admin/kp/drone-models/{id}")
    public ApiResponse<KpDtos.DroneModelDto> updateModel(HttpServletRequest request, @PathVariable UUID id, @RequestBody @Valid KpDtos.DroneModelUpsertRequest body) {
        guard.requireAdmin(request);
        return ApiResponse.of(service.upsertModel(id, body));
    }

    @DeleteMapping("/admin/kp/drone-models/{id}")
    public ResponseEntity<Void> deleteModel(HttpServletRequest request, @PathVariable UUID id) {
        guard.requireAdmin(request);
        service.deleteModel(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/manager/kp/catalog/parts")
    public ApiResponse<List<KpDtos.CatalogItemDto>> managerParts(HttpServletRequest request, @RequestParam(required = false) String q,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "50") int size) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(partsClient.listParts(q, page, size));
    }

    @GetMapping("/manager/kp/catalog/kits")
    public ApiResponse<List<KpDtos.CatalogItemDto>> managerKits(HttpServletRequest request, @RequestParam(required = false) String q,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "50") int size) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(partsClient.listKits(q, page, size));
    }

    @GetMapping("/manager/kp/catalog/kits/{id}")
    public ApiResponse<KpDtos.KitCatalogDetailDto> managerKitDetail(HttpServletRequest request, @PathVariable UUID id) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(partsClient.getKitById(id));
    }

    @GetMapping("/manager/kp/kit-preset")
    public ApiResponse<KpDtos.KitPresetDto> kitPreset(HttpServletRequest request, @RequestParam UUID modelId) {
        guard.requireManagerOrAdmin(request);
        var model = service.listModels(false).stream()
                .filter(m -> m.id().equals(modelId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Drone model not found"));
        return ApiResponse.of(kitPresetService.presetFor(model.code()));
    }

    @PostMapping("/manager/kp/proposals")
    public ApiResponse<KpDtos.ProposalDto> createDraft(HttpServletRequest request, @RequestBody @Valid KpDtos.ProposalUpsertRequest body) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(service.saveDraft(guard.userId(request), guard.username(request), null, body));
    }

    @PostMapping("/manager/kp/proposals/from-preset")
    public ApiResponse<KpDtos.ProposalDto> createFromPreset(HttpServletRequest request,
                                                            @RequestBody @Valid KpDtos.CreateFromPresetRequest body) {
        guard.requireManagerOrAdmin(request);
        var model = service.listModels(false).stream()
                .filter(m -> m.id().equals(body.droneModelId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Drone model not found"));
        var preset = kitPresetService.presetFor(model.code());
        var lines = preset.lines().stream()
                .map(l -> new KpDtos.ProposalLineRequest(
                        l.lineType(), l.refId(), l.sku(), l.name(), l.qty(), l.unitPrice(), l.discountPct(), List.of()))
                .toList();
        var upsert = new KpDtos.ProposalUpsertRequest(body.recipient(), body.droneModelId(), preset.dronePrice(), lines);
        return ApiResponse.of(service.saveDraft(guard.userId(request), guard.username(request), null, upsert));
    }

    @PutMapping("/manager/kp/proposals/{id}")
    public ApiResponse<KpDtos.ProposalDto> updateDraft(HttpServletRequest request, @PathVariable UUID id, @RequestBody @Valid KpDtos.ProposalUpsertRequest body) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(service.saveDraft(guard.userId(request), guard.username(request), id, body));
    }

    @GetMapping("/manager/kp/proposals")
    public ApiResponse<List<KpDtos.ProposalDto>> myProposals(HttpServletRequest request) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(service.listForManager(guard.userId(request)));
    }

    @GetMapping("/manager/kp/proposals/{id}")
    public ApiResponse<KpDtos.ProposalDto> getProposal(HttpServletRequest request, @PathVariable UUID id) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(service.getById(id, guard.userId(request), false));
    }

    @GetMapping("/admin/kp/proposals")
    public ApiResponse<List<KpDtos.ProposalDto>> allProposals(HttpServletRequest request) {
        guard.requireAdmin(request);
        return ApiResponse.of(service.listAll());
    }

    @PostMapping("/manager/kp/proposals/{id}/finalize")
    public ApiResponse<KpDtos.ProposalDto> finalizeProposal(HttpServletRequest request, @PathVariable UUID id) {
        guard.requireManagerOrAdmin(request);
        return ApiResponse.of(service.finalizeProposal(id, guard.userId(request), false));
    }

    @GetMapping("/manager/kp/proposals/{id}/pdf")
    public ResponseEntity<byte[]> managerPdf(HttpServletRequest request, @PathVariable UUID id) throws IOException {
        guard.requireManagerOrAdmin(request);
        var path = service.pdfPath(id, guard.userId(request), false);
        return pdfResponse(path.getFileName().toString(), Files.readAllBytes(path));
    }

    @GetMapping("/admin/kp/proposals/{id}/pdf")
    public ResponseEntity<byte[]> adminPdf(HttpServletRequest request, @PathVariable UUID id) throws IOException {
        guard.requireAdmin(request);
        var path = service.pdfPath(id, guard.userId(request), true);
        return pdfResponse(path.getFileName().toString(), Files.readAllBytes(path));
    }

    private ResponseEntity<byte[]> pdfResponse(String filename, byte[] bytes) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
