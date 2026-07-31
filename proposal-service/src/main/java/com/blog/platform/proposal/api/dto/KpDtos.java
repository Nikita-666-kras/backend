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
            boolean active,
            boolean hasZipPackage
    ) {}

    public record ZipItemDto(
            UUID id,
            String name,
            String sku,
            Integer qty,
            BigDecimal unitPrice,
            Integer sortOrder
    ) {}

    public record ZipItemUpsert(
            @NotBlank String name,
            String sku,
            @NotNull @Min(1) Integer qty,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            Integer sortOrder
    ) {}

    public record ZipPackageDto(
            UUID droneModelId,
            String name,
            BigDecimal price,
            List<ZipItemDto> items
    ) {
        public ZipPackageDto {
            if (items == null) items = List.of();
        }
    }

    public record ZipPackageUpsertRequest(
            String name,
            BigDecimal price,
            @Valid List<ZipItemUpsert> items
    ) {
        public ZipPackageUpsertRequest {
            if (items == null) items = List.of();
        }
    }

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
            @NotNull @Min(1) Integer kitQty,
            @NotNull @DecimalMin("0.00") BigDecimal unitKitPrice,
            @Valid List<ProposalLineRequest> extraLines
    ) {
        public ProposalUpsertRequest {
            if (extraLines == null) {
                extraLines = List.of();
            }
        }
    }

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
            Integer kitQty,
            BigDecimal unitKitPrice,
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
            BigDecimal startPrice,
            String vatMode,
            List<KitPresetLineDto> lines
    ) {}

    public record CalcPreviewDto(
            String priceKey,
            String vatMode,
            Integer kitQty,
            BigDecimal startPrice,
            BigDecimal unitKitPrice,
            BigDecimal priceDiff,
            BigDecimal baseDronePrice,
            BigDecimal unitDronePrice,
            BigDecimal droneTotal,
            BigDecimal grandTotal,
            BigDecimal ndsTotal,
            List<KitPresetLineDto> lines
    ) {}

    public record CreateFromPresetRequest(
            @NotBlank String recipient,
            @NotNull UUID droneModelId
    ) {}

    public record CalculatorRequest(
            @NotNull UUID droneModelId,
            @NotNull @Min(1) Integer kitQty,
            @NotNull @DecimalMin("0.00") BigDecimal unitKitPrice
    ) {}
}
