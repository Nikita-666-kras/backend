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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.searchParts(q, status, droneId, categoryId, page, size)));
    }

    @GetMapping("/parts/{id}")
    public ResponseEntity<ApiResponse<PartResponse>> getPart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.getPart(id)));
    }

    @PostMapping("/parts")
    public ResponseEntity<ApiResponse<PartResponse>> createPart(
            HttpServletRequest request,
            @Valid @RequestBody PartRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.createPart(body)));
    }

    @PutMapping("/parts/{id}")
    public ResponseEntity<ApiResponse<PartResponse>> updatePart(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody PartRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updatePart(id, body)));
    }

    @PostMapping("/parts/{id}/publish")
    public ResponseEntity<ApiResponse<PartResponse>> publishPart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updatePartStatus(id, "PUBLISHED")));
    }

    @PostMapping("/parts/{id}/archive")
    public ResponseEntity<ApiResponse<PartResponse>> archivePart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updatePartStatus(id, "ARCHIVED")));
    }

    @DeleteMapping("/parts/{id}")
    public ResponseEntity<Void> deletePart(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deletePart(id);
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
            accessGuard.requireEditorOrAdmin(request);
        }
        return ResponseEntity.ok(ApiResponse.of(adminPartsBulkService.bulk(body)));
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
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.searchKits(q, status, droneId, page, size)));
    }

    @GetMapping("/kits/{id}")
    public ResponseEntity<ApiResponse<KitResponse>> getKit(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.getKit(id)));
    }

    @PostMapping("/kits")
    public ResponseEntity<ApiResponse<KitResponse>> createKit(
            HttpServletRequest request,
            @Valid @RequestBody KitRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.createKit(body)));
    }

    @PutMapping("/kits/{id}")
    public ResponseEntity<ApiResponse<KitResponse>> updateKit(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody KitRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updateKit(id, body)));
    }

    @PostMapping("/kits/{id}/publish")
    public ResponseEntity<ApiResponse<KitResponse>> publishKit(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updateKitStatus(id, "PUBLISHED")));
    }

    @DeleteMapping("/kits/{id}")
    public ResponseEntity<Void> deleteKit(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deleteKit(id);
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
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.searchDrones(q, status, page, size)));
    }

    @GetMapping("/drones/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> getDrone(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.getDrone(id)));
    }

    @PostMapping("/drones")
    public ResponseEntity<ApiResponse<DroneResponse>> createDrone(
            HttpServletRequest request,
            @Valid @RequestBody DroneRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.createDrone(body)));
    }

    @PutMapping("/drones/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> updateDrone(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody DroneRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updateDrone(id, body)));
    }

    @PostMapping("/drones/{id}/publish")
    public ResponseEntity<ApiResponse<DroneResponse>> publishDrone(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updateDroneStatus(id, "PUBLISHED")));
    }

    @DeleteMapping("/drones/{id}")
    public ResponseEntity<Void> deleteDrone(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deleteDrone(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/part-categories")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> listCategories(HttpServletRequest request) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.listCategories()));
    }

    @PostMapping("/part-categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            HttpServletRequest request,
            @Valid @RequestBody CategoryRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.createCategory(body)));
    }

    @PutMapping("/part-categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            HttpServletRequest request,
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest body
    ) {
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.updateCategory(id, body)));
    }

    @DeleteMapping("/part-categories/{id}")
    public ResponseEntity<Void> deleteCategory(HttpServletRequest request, @PathVariable UUID id) {
        accessGuard.requireAdmin(request);
        partsServiceClient.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/parts/import/template.csv")
    public ResponseEntity<byte[]> importTemplate(HttpServletRequest request) {
        accessGuard.requireEditorOrAdmin(request);
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
        accessGuard.requireEditorOrAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.previewImport(file)));
    }

    @PostMapping(value = "/parts/import/apply", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PartsAdminDtos.ImportApplyResponse>> applyImport(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file,
            @RequestPart(value = "options", required = false) PartsAdminDtos.ImportApplyRequest options
    ) {
        accessGuard.requireEditorOrAdmin(request);
        PartsAdminDtos.ImportApplyRequest body = options == null
                ? new PartsAdminDtos.ImportApplyRequest(null, true, true, true, "DRAFT")
                : options;
        return ResponseEntity.ok(ApiResponse.of(partsServiceClient.applyImport(file, body)));
    }
}
