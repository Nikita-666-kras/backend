package com.blog.platform.parts.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.parts.service.CatalogMediaAccessService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog/media")
@RequiredArgsConstructor
public class CatalogMediaAccessController {

    private final CatalogMediaAccessService catalogMediaAccessService;

    @GetMapping("/{mediaId}/public")
    public ResponseEntity<ApiResponse<Boolean>> isPublic(@PathVariable UUID mediaId) {
        return ResponseEntity.ok(ApiResponse.of(catalogMediaAccessService.isReferencedByPublished(mediaId)));
    }
}
