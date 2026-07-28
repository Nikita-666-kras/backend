package com.blog.platform.proposal.client;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.security.InternalHeaders;
import com.blog.platform.proposal.api.dto.KpDtos.CatalogItemDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class PartsCatalogClient {

    private final RestClient restClient;

    public List<CatalogItemDto> listParts(String q, int page, int size) {
        var path = UriComponentsBuilder.fromPath("/parts")
                .queryParam("status", "PUBLISHED")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("q", java.util.Optional.ofNullable(q))
                .build(true)
                .toUriString();
        ApiResponse<PageResponse<PartRow>> res = restClient.get().uri(path).retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return requireData(res).content().stream()
                .map(p -> new CatalogItemDto(p.id(), p.sku(), p.name(), p.price(), p.currency()))
                .toList();
    }

    public List<CatalogItemDto> listKits(String q, int page, int size) {
        var path = UriComponentsBuilder.fromPath("/kits")
                .queryParam("status", "PUBLISHED")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("q", java.util.Optional.ofNullable(q))
                .build(true)
                .toUriString();
        ApiResponse<PageResponse<KitRow>> res = restClient.get().uri(path).retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return requireData(res).content().stream()
                .map(k -> new CatalogItemDto(k.id(), k.sku(), k.name(), k.price(), k.currency()))
                .toList();
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) throw new IllegalStateException("Parts service empty response");
        return response.data();
    }

    public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {}
    public record PartRow(UUID id, String name, String sku, BigDecimal price, String currency) {}
    public record KitRow(UUID id, String name, String sku, BigDecimal price, String currency) {}

    @org.springframework.context.annotation.Configuration
    static class ClientConfig {
        @org.springframework.context.annotation.Bean
        RestClient partsRestClient(
                @Value("${parts-service.base-url}") String baseUrl,
                @Value("${security.internal-api-key}") String apiKey
        ) {
            return RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(InternalHeaders.API_KEY, apiKey)
                    .build();
        }
    }
}
