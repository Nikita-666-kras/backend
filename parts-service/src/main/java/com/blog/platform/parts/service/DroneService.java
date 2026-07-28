package com.blog.platform.parts.service;

import com.blog.platform.parts.api.dto.PartsDtos.DroneRequest;
import com.blog.platform.parts.api.dto.PartsDtos.DroneResponse;
import com.blog.platform.parts.api.dto.PartsDtos.PageResponse;
import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.domain.Drone;
import com.blog.platform.parts.repository.DroneRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DroneService {

    private final DroneRepository droneRepository;
    private final SlugService slugService;

    @Transactional(readOnly = true)
    public PageResponse<DroneResponse> search(String q, CatalogStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, clamp(size), Sort.by(Sort.Direction.ASC, "sortOrder", "name"));
        Page<Drone> result = droneRepository.search(blankToNull(q), status, pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public DroneResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public DroneResponse getBySlug(String slug) {
        return toResponse(droneRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Дрон не найден")));
    }

    @Transactional
    public DroneResponse create(DroneRequest request) {
        Drone drone = new Drone();
        apply(drone, request, true);
        return toResponse(droneRepository.save(drone));
    }

    @Transactional
    public DroneResponse update(UUID id, DroneRequest request) {
        Drone drone = require(id);
        apply(drone, request, false);
        return toResponse(droneRepository.save(drone));
    }

    @Transactional
    public DroneResponse updateStatus(UUID id, CatalogStatus status) {
        Drone drone = require(id);
        drone.setStatus(status);
        return toResponse(droneRepository.save(drone));
    }

    @Transactional
    public void delete(UUID id) {
        droneRepository.delete(require(id));
    }

    private void apply(Drone drone, DroneRequest request, boolean create) {
        drone.setName(request.name().trim());
        String slugSource = request.slug() == null || request.slug().isBlank() ? request.name() : request.slug();
        if (create || request.slug() != null) {
            String slug = slugService.uniqueSlug(slugSource, candidate -> {
                if (drone.getId() == null) {
                    return droneRepository.existsBySlug(candidate);
                }
                return droneRepository.findBySlug(candidate)
                        .filter(existing -> !existing.getId().equals(drone.getId()))
                        .isPresent();
            });
            drone.setSlug(slug);
        }
        drone.setDescription(blankToNull(request.description()));
        drone.setImageMediaId(request.imageMediaId());
        drone.setStatus(request.status() == null ? CatalogStatus.DRAFT : request.status());
        drone.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    Drone require(UUID id) {
        return droneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Дрон не найден"));
    }

    private PageResponse<DroneResponse> toPage(Page<Drone> page) {
        return new PageResponse<>(
                page.map(this::toResponse).getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    private DroneResponse toResponse(Drone drone) {
        return new DroneResponse(
                drone.getId(),
                drone.getName(),
                drone.getSlug(),
                drone.getDescription(),
                drone.getImageMediaId(),
                mediaUrl(drone.getImageMediaId()),
                drone.getStatus(),
                drone.getSortOrder(),
                drone.getCreatedAt(),
                drone.getUpdatedAt()
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
