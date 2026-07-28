package com.blog.platform.parts.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.parts.api.dto.PartsDtos.PageResponse;
import com.blog.platform.parts.api.dto.PartsDtos.PartRequest;
import com.blog.platform.parts.api.dto.PartsDtos.PartResponse;
import com.blog.platform.parts.api.dto.PartsDtos.StatusBody;
import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.service.PartService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/parts")
@RequiredArgsConstructor
public class PartController {

    private final PartService partService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PartResponse>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(required = false) UUID droneId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(partService.search(q, status, droneId, categoryId, page, size)));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<ApiResponse<PartResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(partService.get(id)));
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<PartResponse>> getBySku(@PathVariable String sku) {
        PartResponse part = partService.getBySku(sku);
        if (part.status() != CatalogStatus.PUBLISHED) {
            throw new IllegalArgumentException("Запчасть не найдена");
        }
        return ResponseEntity.ok(ApiResponse.of(part));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PartResponse>> create(@Valid @RequestBody PartRequest request) {
        return ResponseEntity.ok(ApiResponse.of(partService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PartResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody PartRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(partService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PartResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusBody body
    ) {
        return ResponseEntity.ok(ApiResponse.of(partService.updateStatus(id, body.status())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        partService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
