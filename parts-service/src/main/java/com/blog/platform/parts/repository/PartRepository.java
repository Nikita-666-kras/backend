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
                LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Part> search(
            @Param("q") String q,
            @Param("status") CatalogStatus status,
            @Param("droneId") UUID droneId,
            @Param("categoryId") UUID categoryId,
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
