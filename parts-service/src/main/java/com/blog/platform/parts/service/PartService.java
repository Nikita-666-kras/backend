package com.blog.platform.parts.service;

import com.blog.platform.parts.api.dto.PartsDtos.PageResponse;
import com.blog.platform.parts.api.dto.PartsDtos.PartRequest;
import com.blog.platform.parts.api.dto.PartsDtos.PartResponse;
import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.domain.ExternalSource;
import com.blog.platform.parts.domain.Kit;
import com.blog.platform.parts.domain.KitItem;
import com.blog.platform.parts.domain.KitPriceMode;
import com.blog.platform.parts.domain.Part;
import com.blog.platform.parts.domain.PartCategory;
import com.blog.platform.parts.repository.KitRepository;
import com.blog.platform.parts.repository.PartCategoryRepository;
import com.blog.platform.parts.repository.PartRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartService {

    private final PartRepository partRepository;
    private final PartCategoryRepository categoryRepository;
    private final KitRepository kitRepository;
    private final DroneService droneService;

    @Transactional(readOnly = true)
    public PageResponse<PartResponse> search(
            String q,
            CatalogStatus status,
            UUID droneId,
            UUID categoryId,
            int page,
            int size
    ) {
        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<Part> result = partRepository.search(smartQuery(q), status, droneId, categoryId, pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public PartResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public PartResponse getBySku(String sku) {
        return toResponse(partRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Запчасть не найдена")));
    }

    @Transactional
    public PartResponse create(PartRequest request) {
        if (partRepository.existsBySku(request.sku().trim())) {
            throw new IllegalArgumentException("Артикул уже существует: " + request.sku());
        }
        Part part = new Part();
        apply(part, request);
        return toResponse(partRepository.save(part));
    }

    @Transactional
    public PartResponse update(UUID id, PartRequest request) {
        Part part = require(id);
        String sku = request.sku().trim();
        if (partRepository.existsBySkuAndIdNot(sku, id)) {
            throw new IllegalArgumentException("Артикул уже существует: " + sku);
        }
        apply(part, request);
        return toResponse(partRepository.save(part));
    }

    @Transactional
    public PartResponse updateStatus(UUID id, CatalogStatus status) {
        Part part = require(id);
        part.setStatus(status);
        return toResponse(partRepository.save(part));
    }

    @Transactional
    public void delete(UUID id) {
        Part part = require(id);
        detachFromKits(id);
        partRepository.delete(part);
    }

    /** kit_items.part_id is ON DELETE RESTRICT — remove refs before deleting the part. */
    private void detachFromKits(UUID partId) {
        List<Kit> kits = kitRepository.findAllByPartId(partId);
        for (Kit kit : kits) {
            if (kit.getItems() == null || kit.getItems().isEmpty()) {
                continue;
            }
            kit.getItems().removeIf(item -> partId.equals(item.getPartId()));
            if (kit.getPriceMode() == KitPriceMode.SUM) {
                kit.setPrice(sumKitItems(kit.getItems()));
            }
            kitRepository.save(kit);
        }
    }

    private BigDecimal sumKitItems(List<KitItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        if (items == null) {
            return sum;
        }
        for (KitItem item : items) {
            if (item.getPart() == null || item.getPart().getPrice() == null) {
                continue;
            }
            int qty = item.getQty() == null ? 1 : item.getQty();
            sum = sum.add(item.getPart().getPrice().multiply(BigDecimal.valueOf(qty)));
        }
        return sum;
    }

    Part require(UUID id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Запчасть не найдена"));
    }

    private void apply(Part part, PartRequest request) {
        part.setName(request.name().trim());
        part.setSku(request.sku().trim());
        part.setDescription(blankToNull(request.description()));
        if (request.price() == null || request.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Цена должна быть >= 0");
        }
        part.setPrice(request.price());
        part.setCurrency(request.currency() == null || request.currency().isBlank() ? "RUB" : request.currency().trim().toUpperCase());
        part.setDrone(request.droneId() == null ? null : droneService.require(request.droneId()));
        part.setCategory(resolveCategory(request.categoryId()));
        part.setCoverMediaId(request.coverMediaId());
        part.setMediaIds(normalizeMedia(request.mediaIds(), request.coverMediaId()));
        part.setStatus(request.status() == null ? CatalogStatus.DRAFT : request.status());
        part.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        part.setExternalSource(request.externalSource() == null ? ExternalSource.MANUAL : request.externalSource());
        part.setExternalId(blankToNull(request.externalId()));
    }

    private PartCategory resolveCategory(UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
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

    private PageResponse<PartResponse> toPage(Page<Part> page) {
        return new PageResponse<>(
                page.map(this::toResponse).getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    private PartResponse toResponse(Part part) {
        List<UUID> mediaIds = part.getMediaIds() == null ? List.of() : List.copyOf(part.getMediaIds());
        return new PartResponse(
                part.getId(),
                part.getName(),
                part.getSku(),
                part.getDescription(),
                part.getPrice(),
                part.getCurrency(),
                part.getDrone() == null ? null : part.getDrone().getId(),
                part.getDrone() == null ? null : part.getDrone().getName(),
                part.getCategory() == null ? null : part.getCategory().getId(),
                part.getCategory() == null ? null : part.getCategory().getName(),
                part.getCoverMediaId(),
                mediaUrl(part.getCoverMediaId()),
                mediaIds,
                mediaIds.stream().map(this::mediaUrl).toList(),
                part.getStatus(),
                part.getSortOrder(),
                part.getExternalSource(),
                part.getExternalId(),
                part.getCreatedAt(),
                part.getUpdatedAt()
        );
    }

    private String mediaUrl(UUID mediaId) {
        return mediaId == null ? null : "/media/" + mediaId;
    }

    private int clamp(int size) {
        return Math.min(Math.max(size, 1), 500);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Мультисловный LIKE: «акб пульт» → акб%пульт (порядок слов сохраняется). */
    private String smartQuery(String value) {
        if (value == null || value.isBlank()) return null;
        String cleaned = value.trim()
                .toLowerCase()
                .replace('ё', 'е')
                .replace("%", "")
                .replace("_", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s+-]", " ")
                .trim()
                .replaceAll("\\s+", "%");
        return cleaned.isBlank() ? null : cleaned;
    }
}
