package com.blog.platform.sso.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.sso.service.AuthService;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalAuthController {

    private final AuthService authService;

    @GetMapping("/token-versions")
    public ResponseEntity<ApiResponse<Map<String, Long>>> tokenVersions() {
        Map<String, Long> versions = authService.activeTokenVersions().entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
        return ResponseEntity.ok(ApiResponse.of(versions));
    }
}
