package com.blog.platform.article.domain;

import com.blog.platform.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "media_files")
public class MediaFile extends BaseEntity {

    @Column(nullable = false, length = 400)
    private String originalName;

    @Column(nullable = false, unique = true, length = 255)
    private String storedName;

    @Column(nullable = false, length = 120)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaKind kind;

    @Column(nullable = false)
    private UUID uploadedBy;
}
