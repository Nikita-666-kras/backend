package com.blog.platform.article.domain;

import com.blog.platform.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "articles")
public class Article extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(nullable = false, length = 400)
    private String shortDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String htmlContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ArticleStatus status = ArticleStatus.DRAFT;

    @Column(nullable = false)
    private Integer readingTime = 1;

    @Column(nullable = false)
    private Long views = 0L;

    @Column(nullable = false)
    private Long likes = 0L;

    @Column(nullable = false)
    private UUID authorId;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "article_media", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "media_object_name", nullable = false)
    private Set<String> mediaObjectNames = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "article_tags", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "tag", nullable = false)
    private Set<String> tags = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "article_categories", joinColumns = @JoinColumn(name = "article_id"))
    @Column(name = "category", nullable = false)
    private Set<String> categories = new HashSet<>();
}
