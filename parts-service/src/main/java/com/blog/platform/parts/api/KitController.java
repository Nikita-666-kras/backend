package com.blog.platform.parts.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.parts.api.dto.PartsDtos.KitRequest;
import com.blog.platform.parts.api.dto.PartsDtos.KitResponse;
import com.blog.platform.parts.api.dto.PartsDtos.PageResponse;
import com.blog.platform.parts.api.dto.PartsDtos.StatusBody;
import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.service.KitService;
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
@RequestMapping("/kits")
@RequiredArgsConstructor
public class KitController {

    private final KitService kitService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<KitResponse>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(required = false) UUID droneId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(kitService.search(q, status, droneId, page, size)));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<ApiResponse<KitResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(kitService.get(id)));
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<KitResponse>> getBySku(@PathVariable String sku) {
        KitResponse kit = kitService.getBySku(sku);
        if (kit.status() != CatalogStatus.PUBLISHED) {
            throw new IllegalArgumentException("Комплект не найден");
        }
        return ResponseEntity.ok(ApiResponse.of(kit));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<KitResponse>> create(@Valid @RequestBody KitRequest request) {
        return ResponseEntity.ok(ApiResponse.of(kitService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<KitResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody KitRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(kitService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<KitResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusBody body
    ) {
        return ResponseEntity.ok(ApiResponse.of(kitService.updateStatus(id, body.status())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        kitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
