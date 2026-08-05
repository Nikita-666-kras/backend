package com.blog.platform.parts.domain;

import com.blog.platform.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "parts")
public class Part extends BaseEntity {

    @Column(nullable = false, length = 240)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "RUB";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drone_id")
    private Drone drone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private PartCategory category;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogStatus status = CatalogStatus.DRAFT;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExternalSource externalSource = ExternalSource.MANUAL;

    @Column(length = 120)
    private String externalId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "part_media", joinColumns = @JoinColumn(name = "part_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "media_id", nullable = false)
    private List<UUID> mediaIds = new ArrayList<>();
}
