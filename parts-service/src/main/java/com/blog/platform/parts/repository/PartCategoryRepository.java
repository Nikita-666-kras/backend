package com.blog.platform.parts.repository;

import com.blog.platform.parts.domain.PartCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartCategoryRepository extends JpaRepository<PartCategory, UUID> {
    Optional<PartCategory> findBySlug(String slug);

    Optional<PartCategory> findByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    List<PartCategory> findAllByOrderBySortOrderAscNameAsc();
}
