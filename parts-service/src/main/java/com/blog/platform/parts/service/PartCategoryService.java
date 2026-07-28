package com.blog.platform.parts.service;

import com.blog.platform.parts.api.dto.PartsDtos.CategoryRequest;
import com.blog.platform.parts.api.dto.PartsDtos.CategoryResponse;
import com.blog.platform.parts.domain.PartCategory;
import com.blog.platform.parts.repository.PartCategoryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartCategoryService {

    private final PartCategoryRepository categoryRepository;
    private final SlugService slugService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        PartCategory category = new PartCategory();
        apply(category, request, true);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request) {
        PartCategory category = require(id);
        apply(category, request, false);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        categoryRepository.delete(require(id));
    }

    private void apply(PartCategory category, CategoryRequest request, boolean create) {
        category.setName(request.name().trim());
        String slugSource = request.slug() == null || request.slug().isBlank() ? request.name() : request.slug();
        if (create || request.slug() != null) {
            String slug = slugService.uniqueSlug(slugSource, candidate -> {
                if (category.getId() == null) {
                    return categoryRepository.existsBySlug(candidate);
                }
                return categoryRepository.findBySlug(candidate)
                        .filter(existing -> !existing.getId().equals(category.getId()))
                        .isPresent();
            });
            category.setSlug(slug);
        }
        if (request.parentId() != null) {
            if (request.parentId().equals(category.getId())) {
                throw new IllegalArgumentException("Категория не может быть родителем самой себе");
            }
            category.setParent(require(request.parentId()));
        } else {
            category.setParent(null);
        }
        category.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
    }

    private PartCategory require(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Категория не найдена"));
    }

    private CategoryResponse toResponse(PartCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getParentId(),
                category.getSortOrder(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
