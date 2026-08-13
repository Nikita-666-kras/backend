package com.blog.platform.integrations.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class OrderDtos {

    private OrderDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateOrderRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 32) String phone,
            @Size(max = 160) String email,
            @NotEmpty @Valid List<OrderItem> items,
            @Valid OrderMeta meta,
            @NotNull Boolean consentPd,
            Boolean consentMailing
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItem(
            @NotBlank @Size(max = 64) String sku,
            @Size(max = 64) String partId,
            @NotBlank @Size(max = 240) String title,
            @Positive Integer qty,
            Double price
    ) {
        public int normalizedQty() {
            return qty == null || qty < 1 ? 1 : qty;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderMeta(
            @Size(max = 64) String source,
            @Size(max = 512) String pageUrl,
            @Valid OrderUtm utm,
            @Size(max = 64) String yaCid
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderUtm(
            @Size(max = 256) String campaign,
            @Size(max = 512) String referrer
    ) {
    }

    public record CreateOrderResponse(
            String orderId,
            Long leadId,
            Long contactId,
            String status
    ) {
    }
}
