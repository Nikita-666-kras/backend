package com.blog.platform.logging.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.logging.api.dto.LoggingDtos.LogIngestRequest;
import com.blog.platform.logging.api.dto.LoggingDtos.LogIngestResponse;
import com.blog.platform.logging.api.dto.LoggingDtos.LogQueryResponse;
import com.blog.platform.logging.api.dto.LoggingDtos.LogStatsResponse;
import com.blog.platform.logging.service.LoggingService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/logs")
@RequiredArgsConstructor
public class LoggingController {

    private final LoggingService loggingService;

    @PostMapping
    public ResponseEntity<ApiResponse<LogIngestResponse>> ingest(@Valid @RequestBody LogIngestRequest request) {
        return ResponseEntity.ok(ApiResponse.of(loggingService.ingest(request.events())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<LogQueryResponse>> query(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(ApiResponse.of(loggingService.query(from, to, level, category, service, q, page, size)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<LogStatsResponse>> stats(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return ResponseEntity.ok(ApiResponse.of(loggingService.stats(from, to)));
    }
}
