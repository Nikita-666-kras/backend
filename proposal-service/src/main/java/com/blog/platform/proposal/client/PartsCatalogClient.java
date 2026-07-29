package com.blog.platform.proposal.client;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.common.security.InternalHeaders;
import com.blog.platform.proposal.api.dto.KpDtos.CatalogItemDto;
import com.blog.platform.proposal.api.dto.KpDtos.KitCatalogDetailDto;
import com.blog.platform.proposal.api.dto.KpDtos.KitCatalogItemDto;
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

    public KitCatalogDetailDto getKitById(UUID id) {
        ApiResponse<KitDetailRow> res = restClient.get()
                .uri("/kits/by-id/{id}", id)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        KitDetailRow kit = requireData(res);
        return new KitCatalogDetailDto(
                kit.id(),
                kit.sku(),
                kit.name(),
                kit.price(),
                kit.currency(),
                kit.items() == null ? List.of() : kit.items().stream()
                        .map(i -> new KitCatalogItemDto(i.partId(), i.partSku(), i.partName(), i.qty(), i.partPrice()))
                        .toList()
        );
    }

    private <T> T requireData(ApiResponse<T> response) {
        if (response == null || response.data() == null) throw new IllegalStateException("Parts service empty response");
        return response.data();
    }

    public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {}
    public record PartRow(UUID id, String name, String sku, BigDecimal price, String currency) {}
    public record KitRow(UUID id, String name, String sku, BigDecimal price, String currency) {}
    public record KitItemRow(UUID partId, String partSku, String partName, Integer qty, BigDecimal partPrice) {}
    public record KitDetailRow(UUID id, String name, String sku, BigDecimal price, String currency, List<KitItemRow> items) {}

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
