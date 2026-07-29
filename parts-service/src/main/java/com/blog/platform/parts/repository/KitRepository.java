package com.blog.platform.parts.repository;

import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.domain.Kit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KitRepository extends JpaRepository<Kit, UUID> {
    Optional<Kit> findBySku(String sku);

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    @Query("""
            SELECT k FROM Kit k
            LEFT JOIN k.drone d
            WHERE (:status IS NULL OR k.status = :status)
              AND (:droneId IS NULL OR d.id = :droneId)
              AND (
                :q IS NULL OR :q = '' OR
                LOWER(k.name) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(k.sku) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Kit> search(
            @Param("q") String q,
            @Param("status") CatalogStatus status,
            @Param("droneId") UUID droneId,
            Pageable pageable
    );

    @Query(value = """
            SELECT COUNT(*) > 0 FROM kits k
            LEFT JOIN kit_media km ON km.kit_id = k.id
            WHERE k.status = 'PUBLISHED'
              AND (k.cover_media_id = :mediaId OR km.media_id = :mediaId)
            """, nativeQuery = true)
    boolean existsPublishedMediaReference(@Param("mediaId") UUID mediaId);
}
