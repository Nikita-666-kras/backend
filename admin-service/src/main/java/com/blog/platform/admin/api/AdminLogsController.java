package com.blog.platform.admin.api;

import com.blog.platform.admin.api.dto.LogsDtos.LogStatsResponse;
import com.blog.platform.admin.api.dto.LogsDtos.LogsPageResponse;
import com.blog.platform.admin.client.LoggingServiceClient;
import com.blog.platform.admin.security.AdminAccessGuard;
import com.blog.platform.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
public class AdminLogsController {

    private final LoggingServiceClient loggingServiceClient;
    private final AdminAccessGuard accessGuard;

    @GetMapping
    public ResponseEntity<ApiResponse<LogsPageResponse>> logs(
            HttpServletRequest request,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        accessGuard.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(loggingServiceClient.query(from, to, level, category, service, q, page, size)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<LogStatsResponse>> stats(
            HttpServletRequest request,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        accessGuard.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.of(loggingServiceClient.stats(from, to)));
    }
}
