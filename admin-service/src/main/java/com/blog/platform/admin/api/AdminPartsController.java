package com.blog.platform.admin.api;

import com.blog.platform.admin.api.dto.AdminDtos.BulkRequest;
import com.blog.platform.admin.api.dto.AdminDtos.BulkResult;
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
import com.blog.platform.admin.client.PartsServiceClient;
import com.blog.platform.admin.security.AdminAccessGuard;
import com.blog.platform.admin.service.AdminPartsBulkService;
import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.logging.AuditClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPartsController {

    private final PartsServiceClient partsServiceClient;
    private final AdminPartsBulkService adminPartsBulkService;
    private final AdminAccessGuard accessGuard;
    private final AuditClient auditClient;

    @GetMapping("/parts")
    public ResponseEntity<ApiResponse<PageResponse<PartResponse>>> listParts(
            HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID droneId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.searchParts(q, status, droneId, categoryId, page, size)));
    }

    @GetMapping("/parts/{id}")
    public ResponseEntity<ApiResponse<PartResponse>> getPart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.getPart(id)));
    }

    @PostMapping("/parts")
    public ResponseEntity<ApiResponse<PartResponse>> createPart(
            HttpServletRequest request,
            @Valid @RequestBody PartRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        PartResponse response = partsServiceClient.createPart(body);
        logCatalog(request, "Part created", Map.of("partId", response.id().toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PutMapping("/parts/{id}")
    public ResponseEntity<ApiResponse<PartResponse>> updatePart(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody PartRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        PartResponse response = partsServiceClient.updatePart(id, body);
        logCatalog(request, "Part updated", Map.of("partId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/parts/{id}/publish")
    public ResponseEntity<ApiResponse<PartResponse>> publishPart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireCatalogOrAdmin(request);
        PartResponse response = partsServiceClient.updatePartStatus(id, "PUBLISHED");
        logCatalog(request, "Part published", Map.of("partId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/parts/{id}/archive")
    public ResponseEntity<ApiResponse<PartResponse>> archivePart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireCatalogOrAdmin(request);
        PartResponse response = partsServiceClient.updatePartStatus(id, "ARCHIVED");
        logCatalog(request, "Part archived", Map.of("partId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/parts/{id}")
    public ResponseEntity<Void> deletePart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deletePart(id);
        logCatalog(request, "Part deleted", Map.of("partId", id.toString()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/parts/bulk")
    public ResponseEntity<ApiResponse<BulkResult>> bulkParts(
            HttpServletRequest request,
            @Valid @RequestBody BulkRequest body
    ) {
        String action = body.action().trim().toUpperCase();
        if ("DELETE".equals(action)) {
            accessGuard.requireAdmin(request);
        } else {
            accessGuard.requireCatalogOrAdmin(request);
        }
        BulkResult result = adminPartsBulkService.bulk(body);
        logCatalog(request, "Parts bulk action", Map.of("action", action, "success", result.success(), "failed", result.failed()));
        return ResponseEntity.ok(ApiResponse.of(result));
    }

    @GetMapping("/kits")
    public ResponseEntity<ApiResponse<PageResponse<KitResponse>>> listKits(
            HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID droneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.searchKits(q, status, droneId, page, size)));
    }

    @GetMapping("/kits/{id}")
    public ResponseEntity<ApiResponse<KitResponse>> getKit(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.getKit(id)));
    }

    @PostMapping("/kits")
    public ResponseEntity<ApiResponse<KitResponse>> createKit(
            HttpServletRequest request,
            @Valid @RequestBody KitRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        KitResponse response = partsServiceClient.createKit(body);
        logCatalog(request, "Kit created", Map.of("kitId", response.id().toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PutMapping("/kits/{id}")
    public ResponseEntity<ApiResponse<KitResponse>> updateKit(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody KitRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        KitResponse response = partsServiceClient.updateKit(id, body);
        logCatalog(request, "Kit updated", Map.of("kitId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/kits/{id}/publish")
    public ResponseEntity<ApiResponse<KitResponse>> publishKit(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireCatalogOrAdmin(request);
        KitResponse response = partsServiceClient.updateKitStatus(id, "PUBLISHED");
        logCatalog(request, "Kit published", Map.of("kitId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/kits/{id}")
    public ResponseEntity<Void> deleteKit(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deleteKit(id);
        logCatalog(request, "Kit deleted", Map.of("kitId", id.toString()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/drones")
    public ResponseEntity<ApiResponse<PageResponse<DroneResponse>>> listDrones(
            HttpServletRequest request,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.searchDrones(q, status, page, size)));
    }

    @GetMapping("/drones/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> getDrone(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.getDrone(id)));
    }

    @PostMapping("/drones")
    public ResponseEntity<ApiResponse<DroneResponse>> createDrone(
            HttpServletRequest request,
            @Valid @RequestBody DroneRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        DroneResponse response = partsServiceClient.createDrone(body);
        logCatalog(request, "Drone created", Map.of("droneId", response.id().toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PutMapping("/drones/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> updateDrone(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody DroneRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        DroneResponse response = partsServiceClient.updateDrone(id, body);
        logCatalog(request, "Drone updated", Map.of("droneId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping("/drones/{id}/publish")
    public ResponseEntity<ApiResponse<DroneResponse>> publishDrone(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireCatalogOrAdmin(request);
        DroneResponse response = partsServiceClient.updateDroneStatus(id, "PUBLISHED");
        logCatalog(request, "Drone published", Map.of("droneId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/drones/{id}")
    public ResponseEntity<Void> deleteDrone(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deleteDrone(id);
        logCatalog(request, "Drone deleted", Map.of("droneId", id.toString()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/part-categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listCategories(HttpServletRequest request) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.listCategories()));
    }

    @PostMapping("/part-categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            HttpServletRequest request,
            @Valid @RequestBody CategoryRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        CategoryResponse response = partsServiceClient.createCategory(body);
        logCatalog(request, "Category created", Map.of("categoryId", response.id().toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PutMapping("/part-categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest body
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        CategoryResponse response = partsServiceClient.updateCategory(id, body);
        logCatalog(request, "Category updated", Map.of("categoryId", id.toString()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/part-categories/{id}")
    public ResponseEntity<Void> deleteCategory(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deleteCategory(id);
        logCatalog(request, "Category deleted", Map.of("categoryId", id.toString()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parts/import/template.csv")
    public ResponseEntity<byte[]> importTemplate(HttpServletRequest request) {
        accessGuard.requireCatalogOrAdmin(request);
        byte[] body = partsServiceClient.importTemplate();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"parts-import-template.csv\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @PostMapping(value = "/parts/import/preview", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PartsAdminDtos.ImportPreviewResponse>> previewImport(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.previewImport(file)));
    }

    @PostMapping(value = "/parts/import/apply", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PartsAdminDtos.ImportApplyResponse>> applyImport(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestPart(value = "options", required = false) PartsAdminDtos.ImportApplyRequest options
    ) {
        accessGuard.requireCatalogOrAdmin(request);
        PartsAdminDtos.ImportApplyRequest body = options == null
                ? new PartsAdminDtos.ImportApplyRequest(null, true, true, true, "DRAFT")
                : options;
        PartsAdminDtos.ImportApplyResponse response = partsServiceClient.applyImport(file, body);
        logCatalog(request, "Parts import applied", Map.of("created", response.created(), "updated", response.updated(), "errors", response.errors() == null ? 0 : response.errors().size()));
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    private void logCatalog(HttpServletRequest request, String message, Map<String, Object> details) {
        auditClient.audit("CATALOG", message, details, accessGuard.userId(request).toString(), null, null);
    }
}

