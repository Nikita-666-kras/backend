package com.blog.platform.proposal.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class KpDtos {
    private KpDtos() {}

    public enum LineType { DRONE, KIT, PART }

    public record DroneModelDto(
            UUID id,
            String code,
            String name,
            BigDecimal defaultPrice,
            Integer sortOrder,
            boolean active
    ) {}

    public record CatalogItemDto(
            UUID id,
            String sku,
            String name,
            BigDecimal price,
            String currency
    ) {}

    public record KitCatalogItemDto(
            UUID partId,
            String partSku,
            String partName,
            Integer qty,
            BigDecimal partPrice
    ) {}

    public record KitCatalogDetailDto(
            UUID id,
            String sku,
            String name,
            BigDecimal price,
            String currency,
            List<KitCatalogItemDto> items
    ) {}

    public record ProposalKitItemDto(
            UUID partId,
            String partSku,
            String partName,
            Integer qty,
            BigDecimal partPrice
    ) {}

    public record ProposalLineRequest(
            @NotNull LineType lineType,
            UUID refId,
            String sku,
            @NotBlank String name,
            @NotNull @Min(1) Integer qty,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @Min(0) @Max(20) Integer discountPct,
            List<ProposalKitItemDto> kitItems
    ) {
        public ProposalLineRequest {
            if (kitItems == null) {
                kitItems = List.of();
            }
        }
    }

    public record ProposalUpsertRequest(
            @NotBlank String recipient,
            @NotNull UUID droneModelId,
            @NotNull @DecimalMin("0.00") BigDecimal dronePrice,
            @NotNull @Valid List<ProposalLineRequest> lines
    ) {}

    public record ProposalLineDto(
            UUID id,
            LineType lineType,
            UUID refId,
            String sku,
            String name,
            Integer qty,
            BigDecimal unitPrice,
            Integer discountPct,
            BigDecimal lineTotal,
            List<ProposalKitItemDto> kitItems
    ) {
        public ProposalLineDto {
            if (kitItems == null) {
                kitItems = List.of();
            }
        }
    }

    public record ProposalDto(
            UUID id,
            Integer number,
            UUID managerId,
            String managerUsername,
            String recipient,
            UUID droneModelId,
            String droneModelName,
            BigDecimal dronePrice,
            String status,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal grandTotal,
            BigDecimal ndsTotal,
            String pdfPath,
            List<ProposalLineDto> lines,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record DroneModelUpsertRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotNull @DecimalMin("0.00") BigDecimal defaultPrice,
            Integer sortOrder,
            Boolean active
    ) {}

    public record KitPresetLineDto(
            LineType lineType,
            UUID refId,
            String sku,
            String name,
            Integer qty,
            BigDecimal unitPrice,
            Integer discountPct
    ) {}

    public record KitPresetDto(
            String code,
            BigDecimal dronePrice,
            List<KitPresetLineDto> lines
    ) {}

    public record CreateFromPresetRequest(
            @NotBlank String recipient,
            @NotNull UUID droneModelId
    ) {}
}
