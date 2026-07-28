package com.blog.platform.parts.service;

import com.blog.platform.parts.api.dto.PartsDtos.KitItemRequest;
import com.blog.platform.parts.api.dto.PartsDtos.KitItemResponse;
import com.blog.platform.parts.api.dto.PartsDtos.KitRequest;
import com.blog.platform.parts.api.dto.PartsDtos.KitResponse;
import com.blog.platform.parts.api.dto.PartsDtos.PageResponse;
import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.domain.Kit;
import com.blog.platform.parts.domain.KitItem;
import com.blog.platform.parts.domain.KitPriceMode;
import com.blog.platform.parts.domain.Part;
import com.blog.platform.parts.repository.KitRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KitService {

    private final KitRepository kitRepository;
    private final PartService partService;
    private final DroneService droneService;

    @Transactional(readOnly = true)
    public PageResponse<KitResponse> search(String q, CatalogStatus status, UUID droneId, int page, int size) {
        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Kit> result = kitRepository.search(blankToNull(q), status, droneId, pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public KitResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public KitResponse getBySku(String sku) {
        return toResponse(kitRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Комплект не найден")));
    }

    @Transactional
    public KitResponse create(KitRequest request) {
        if (kitRepository.existsBySku(request.sku().trim())) {
            throw new IllegalArgumentException("Артикул комплекта уже существует: " + request.sku());
        }
        Kit kit = new Kit();
        apply(kit, request);
        return toResponse(kitRepository.save(kit));
    }

    @Transactional
    public KitResponse update(UUID id, KitRequest request) {
        Kit kit = require(id);
        String sku = request.sku().trim();
        if (kitRepository.existsBySkuAndIdNot(sku, id)) {
            throw new IllegalArgumentException("Артикул комплекта уже существует: " + sku);
        }
        apply(kit, request);
        return toResponse(kitRepository.save(kit));
    }

    @Transactional
    public KitResponse updateStatus(UUID id, CatalogStatus status) {
        Kit kit = require(id);
        kit.setStatus(status);
        return toResponse(kitRepository.save(kit));
    }

    @Transactional
    public void delete(UUID id) {
        kitRepository.delete(require(id));
    }

    private void apply(Kit kit, KitRequest request) {
        kit.setName(request.name().trim());
        kit.setSku(request.sku().trim());
        kit.setDescription(blankToNull(request.description()));
        kit.setCurrency(request.currency() == null || request.currency().isBlank() ? "RUB" : request.currency().trim().toUpperCase());
        kit.setPriceMode(request.priceMode() == null ? KitPriceMode.MANUAL : request.priceMode());
        kit.setDrone(request.droneId() == null ? null : droneService.require(request.droneId()));
        kit.setCoverMediaId(request.coverMediaId());
        kit.setMediaIds(normalizeMedia(request.mediaIds(), request.coverMediaId()));
        kit.setStatus(request.status() == null ? CatalogStatus.DRAFT : request.status());
        kit.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        kit.setItems(buildItems(request.items()));
        kit.setPrice(resolvePrice(kit.getPriceMode(), request.price(), kit.getItems()));
    }

    private List<KitItem> buildItems(List<KitItemRequest> items) {
        Map<UUID, KitItem> unique = new LinkedHashMap<>();
        if (items != null) {
            for (KitItemRequest item : items) {
                if (item == null || item.partId() == null) {
                    continue;
                }
                Part part = partService.require(item.partId());
                KitItem kitItem = new KitItem();
                kitItem.setPart(part);
                kitItem.setQty(item.qty() == null || item.qty() < 1 ? 1 : item.qty());
                unique.put(part.getId(), kitItem);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private BigDecimal resolvePrice(KitPriceMode mode, BigDecimal requested, List<KitItem> items) {
        if (mode == KitPriceMode.SUM) {
            BigDecimal sum = BigDecimal.ZERO;
            for (KitItem item : items) {
                sum = sum.add(item.getPart().getPrice().multiply(BigDecimal.valueOf(item.getQty())));
            }
            return sum;
        }
        if (requested == null || requested.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Цена комплекта должна быть >= 0");
        }
        return requested;
    }

    private List<UUID> normalizeMedia(List<UUID> mediaIds, UUID coverMediaId) {
        List<UUID> result = new ArrayList<>();
        if (mediaIds != null) {
            for (UUID id : mediaIds) {
                if (id != null && !result.contains(id)) {
                    result.add(id);
                }
            }
        }
        if (coverMediaId != null && !result.contains(coverMediaId)) {
            result.add(0, coverMediaId);
        }
        return result;
    }

    private Kit require(UUID id) {
        return kitRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Комплект не найден"));
    }

    private PageResponse<KitResponse> toPage(Page<Kit> page) {
        return new PageResponse<>(
                page.map(this::toResponse).getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    private KitResponse toResponse(Kit kit) {
        List<UUID> mediaIds = kit.getMediaIds() == null ? List.of() : List.copyOf(kit.getMediaIds());
        List<KitItemResponse> items = kit.getItems() == null
                ? List.of()
                : kit.getItems().stream().map(this::toItemResponse).toList();
        return new KitResponse(
                kit.getId(),
                kit.getName(),
                kit.getSku(),
                kit.getDescription(),
                kit.getPrice(),
                kit.getCurrency(),
                kit.getPriceMode(),
                kit.getDrone() == null ? null : kit.getDrone().getId(),
                kit.getDrone() == null ? null : kit.getDrone().getName(),
                kit.getCoverMediaId(),
                mediaUrl(kit.getCoverMediaId()),
                mediaIds,
                mediaIds.stream().map(this::mediaUrl).toList(),
                items,
                kit.getStatus(),
                kit.getSortOrder(),
                kit.getCreatedAt(),
                kit.getUpdatedAt()
        );
    }

    private KitItemResponse toItemResponse(KitItem item) {
        Part part = item.getPart();
        return new KitItemResponse(
                part.getId(),
                part.getSku(),
                part.getName(),
                item.getQty(),
                part.getPrice()
        );
    }

    private String mediaUrl(UUID mediaId) {
        return mediaId == null ? null : "/media/" + mediaId;
    }

    private int clamp(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
