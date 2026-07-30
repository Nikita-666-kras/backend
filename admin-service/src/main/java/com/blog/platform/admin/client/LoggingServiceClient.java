package com.blog.platform.admin.client;

import com.blog.platform.admin.api.dto.LogsDtos.LogStatsResponse;
import com.blog.platform.admin.api.dto.LogsDtos.LogsPageResponse;
import com.blog.platform.common.api.ApiResponse;
import java.time.Instant;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class LoggingServiceClient {

    private final RestClient restClient;

    public LoggingServiceClient(RestClient loggingServiceRestClient) {
        this.restClient = loggingServiceRestClient;
    }

    public LogsPageResponse query(
            Instant from,
            Instant to,
            String level,
            String category,
            String service,
            String q,
            int page,
            int size
    ) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/logs")
                        .queryParamIfPresent("from", java.util.Optional.ofNullable(from))
                        .queryParamIfPresent("to", java.util.Optional.ofNullable(to))
                        .queryParamIfPresent("level", java.util.Optional.ofNullable(level))
                        .queryParamIfPresent("category", java.util.Optional.ofNullable(category))
                        .queryParamIfPresent("service", java.util.Optional.ofNullable(service))
                        .queryParamIfPresent("q", java.util.Optional.ofNullable(q))
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<LogsPageResponse>>() {
                })
                .data();
    }

    public LogStatsResponse stats(Instant from, Instant to) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/internal/logs/stats")
                        .queryParamIfPresent("from", java.util.Optional.ofNullable(from))
                        .queryParamIfPresent("to", java.util.Optional.ofNullable(to))
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<ApiResponse<LogStatsResponse>>() {
                })
                .data();
    }
}
