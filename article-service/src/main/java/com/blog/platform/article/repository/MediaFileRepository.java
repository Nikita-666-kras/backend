package com.blog.platform.article.repository;

import com.blog.platform.article.domain.MediaFile;
import com.blog.platform.article.domain.MediaKind;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {
    Page<MediaFile> findByKind(MediaKind kind, Pageable pageable);

    Page<MediaFile> findByOriginalNameContainingIgnoreCase(String q, Pageable pageable);

    Page<MediaFile> findByKindAndOriginalNameContainingIgnoreCase(MediaKind kind, String q, Pageable pageable);
}
