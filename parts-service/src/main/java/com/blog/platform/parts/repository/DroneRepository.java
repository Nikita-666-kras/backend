package com.blog.platform.parts.repository;

import com.blog.platform.parts.domain.CatalogStatus;
import com.blog.platform.parts.domain.Drone;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DroneRepository extends JpaRepository<Drone, UUID> {
    Optional<Drone> findBySlug(String slug);

    Optional<Drone> findByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);

    Page<Drone> findByStatus(CatalogStatus status, Pageable pageable);

    @Query("""
            SELECT d FROM Drone d
            WHERE (:status IS NULL OR d.status = :status)
              AND (
                :q IS NULL OR :q = '' OR
                LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%')) OR
                LOWER(d.slug) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Drone> search(@Param("q") String q, @Param("status") CatalogStatus status, Pageable pageable);
}
