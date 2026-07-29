package com.blog.platform.sso.service;

import com.blog.platform.common.exception.TooManyRequestsException;
import com.blog.platform.sso.domain.AuthRateBucket;
import com.blog.platform.sso.domain.AuthUser;
import com.blog.platform.sso.repository.AuthRateBucketRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private final AuthRateBucketRepository bucketRepository;

    @Value("${security.login.max-failures:5}")
    private int maxFailures;

    @Value("${security.login.lockout-minutes:15}")
    private int lockoutMinutes;

    @Value("${security.login.ip-limit-per-minute:20}")
    private int ipLimitPerMinute;

    @Value("${security.login.refresh-limit-per-minute:30}")
    private int refreshLimitPerMinute;

    @Transactional(readOnly = true)
    public void ensureLoginAllowed(AuthUser user, String ipAddress) {
        checkIpLimit("login:" + normalizeIp(ipAddress), ipLimitPerMinute);
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new TooManyRequestsException("Account temporarily locked. Try again later.");
        }
    }

    @Transactional(readOnly = true)
    public void ensureRefreshAllowed(String ipAddress) {
        checkIpLimit("refresh:" + normalizeIp(ipAddress), refreshLimitPerMinute);
    }

    @Transactional
    public void onLoginFailure(AuthUser user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailures) {
            user.setLockedUntil(Instant.now().plusSeconds(lockoutMinutes * 60L));
        }
    }

    @Transactional
    public void onLoginSuccess(AuthUser user) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
    }

    private void checkIpLimit(String bucketKey, int limit) {
        long now = Instant.now().getEpochSecond();
        AuthRateBucket bucket = bucketRepository.findById(bucketKey).orElseGet(() -> {
            AuthRateBucket created = new AuthRateBucket();
            created.setBucketKey(bucketKey);
            created.setWindowStartEpoch(now);
            created.setRequestCount(0);
            return created;
        });

        if (now - bucket.getWindowStartEpoch() > 60) {
            bucket.setWindowStartEpoch(now);
            bucket.setRequestCount(0);
        }
        bucket.setRequestCount(bucket.getRequestCount() + 1);
        bucketRepository.save(bucket);
        if (bucket.getRequestCount() > limit) {
            throw new TooManyRequestsException("Too many requests. Try again later.");
        }
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "unknown";
        }
        return ipAddress.trim();
    }
}
