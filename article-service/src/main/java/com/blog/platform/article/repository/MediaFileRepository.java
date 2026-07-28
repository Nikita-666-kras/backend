package com.blog.platform.article.repository;

import com.blog.platform.article.domain.MediaFile;
import com.blog.platform.article.domain.MediaKind;
import com.blog.platform.article.domain.MediaSection;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID>, JpaSpecificationExecutor<MediaFile> {
    Page<MediaFile> findByKind(MediaKind kind, Pageable pageable);

    Page<MediaFile> findByOriginalNameContainingIgnoreCase(String q, Pageable pageable);

    Page<MediaFile> findByKindAndOriginalNameContainingIgnoreCase(MediaKind kind, String q, Pageable pageable);

    Page<MediaFile> findBySection(MediaSection section, Pageable pageable);

    Page<MediaFile> findBySectionAndOriginalNameContainingIgnoreCase(MediaSection section, String q, Pageable pageable);

    Page<MediaFile> findByKindAndSection(MediaKind kind, MediaSection section, Pageable pageable);

    Page<MediaFile> findByKindAndSectionAndOriginalNameContainingIgnoreCase(
            MediaKind kind,
            MediaSection section,
            String q,
            Pageable pageable
    );

    List<MediaFile> findByIdIn(Collection<UUID> ids);
}
