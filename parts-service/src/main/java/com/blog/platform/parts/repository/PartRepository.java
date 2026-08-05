package com.blog.platform.parts.repository;

import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.domain.Part;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartRepository extends JpaRepository<Part, UUID> {
    Optional<Part> findBySku(String sku);

    Optional<Part> findByExternalId(String externalId);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    @Query("""
            SELECT p FROM Part p
            LEFT JOIN p.drone d
            LEFT JOIN p.category c
            WHERE (:status IS NULL OR p.status = :status)
              AND (:droneId IS NULL OR d.id = :droneId)
              AND (:categoryId IS NULL OR c.id = :categoryId)
              AND (
                :q IS NULL OR :q = '' OR
                LOWER(CONCAT(
                  COALESCE(p.name, ''), ' ',
                  COALESCE(p.sku, ''), ' ',
                  COALESCE(p.description, ''), ' ',
                  COALESCE(d.name, ''), ' ',
                  COALESCE(c.name, '')
                )) LIKE LOWER(CONCAT('%', :q, '%'))
              )
              AND (
                :catalogFilterKey IS NULL OR :catalogFilterKey = '' OR
                (:catalogFilterKey = 'NO_PRICE' AND (p.price IS NULL OR p.price <= 0)) OR
                (:catalogFilterKey = 'NO_NAME' AND LOWER(p.name) = LOWER(p.sku)) OR
                (:catalogFilterKey = 'NO_PHOTO' AND p.coverMediaId IS NULL) OR
                (:catalogFilterKey = 'NO_DRONE' AND p.drone IS NULL) OR
                (:catalogFilterKey = 'NO_CATEGORY' AND p.category IS NULL) OR
                (:catalogFilterKey = 'INCOMPLETE' AND (
                  p.price IS NULL OR p.price <= 0 OR
                  LOWER(p.name) = LOWER(p.sku) OR
                  p.coverMediaId IS NULL OR
                  p.drone IS NULL OR p.category IS NULL
                ))
              )
            """)
    Page<Part> search(
            @Param("q") String q,
            @Param("status") CatalogStatus status,
            @Param("droneId") UUID droneId,
            @Param("categoryId") UUID categoryId,
            @Param("catalogFilterKey") String catalogFilterKey,
            Pageable pageable
    );

    @Query(value = """
            SELECT COUNT(*) > 0 FROM parts p
            LEFT JOIN part_media pm ON pm.part_id = p.id
            WHERE p.status = 'PUBLISHED'
              AND (p.cover_media_id = :mediaId OR pm.media_id = :mediaId)
            """, nativeQuery = true)
    boolean existsPublishedMediaReference(@Param("mediaId") UUID mediaId);
}
