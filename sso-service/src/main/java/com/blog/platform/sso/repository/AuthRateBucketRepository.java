package com.blog.platform.sso.repository;

import com.blog.platform.sso.domain.AuthRateBucket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRateBucketRepository extends JpaRepository<AuthRateBucket, String> {
}
