package com.blog.platform.parts.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.exception.UnauthorizedException;
import com.blog.platform.parts.api.dto.PartsDtos.DroneRequest;
import com.blog.platform.parts.api.dto.PartsDtos.DroneResponse;
import com.blog.platform.parts.api.dto.PartsDtos.PageResponse;
import com.blog.platform.parts.api.dto.PartsDtos.StatusBody;
import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.security.TrustedInternalRequest;
import com.blog.platform.parts.service.DroneService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/drones")
@RequiredArgsConstructor
public class DroneController {

    private final DroneService droneService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DroneResponse>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request
    ) {
        CatalogStatus effectiveStatus = TrustedInternalRequest.isTrusted(request) ? status : CatalogStatus.PUBLISHED;
        return ResponseEntity.ok(ApiResponse.of(droneService.search(q, effectiveStatus, page, size)));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> getById(@PathVariable UUID id, HttpServletRequest request) {
        if (!TrustedInternalRequest.isTrusted(request)) {
            throw new UnauthorizedException("Authentication required");
        }
        return ResponseEntity.ok(ApiResponse.of(droneService.get(id)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<DroneResponse>> getBySlug(@PathVariable String slug) {
        DroneResponse drone = droneService.getBySlug(slug);
        if (drone.status() != CatalogStatus.PUBLISHED) {
            throw new IllegalArgumentException("Дрон не найден");
        }
        return ResponseEntity.ok(ApiResponse.of(drone));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DroneResponse>> create(@Valid @RequestBody DroneRequest request) {
        return ResponseEntity.ok(ApiResponse.of(droneService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DroneResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody DroneRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(droneService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<DroneResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusBody body
    ) {
        return ResponseEntity.ok(ApiResponse.of(droneService.updateStatus(id, body.status())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        droneService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
