package com.blog.platform.sso.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "auth_rate_buckets")
public class AuthRateBucket {

    @Id
    @Column(name = "bucket_key", nullable = false, length = 120)
    private String bucketKey;

    @Column(name = "window_start_epoch", nullable = false)
    private long windowStartEpoch;

    @Column(name = "request_count", nullable = false)
    private int requestCount;
}
