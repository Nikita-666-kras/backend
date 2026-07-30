package com.blog.platform.sso.service;

import com.blog.platform.common.exception.TooManyRequestsException;
import com.blog.platform.sso.domain.AuthRateBucket;
import com.blog.platform.sso.domain.AuthUser;
import com.blog.platform.sso.repository.AuthRateBucketRepository;
import com.blog.platform.sso.repository.AuthUserRepository;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginRateLimitService {

    private final AuthRateBucketRepository bucketRepository;
    private final AuthUserRepository authUserRepository;

    @Value("${security.login.max-failures:5}")
    private int maxFailures;

    @Value("${security.login.lockout-minutes:15}")
    private int lockoutMinutes;

    @Value("${security.login.ip-limit-per-minute:20}")
    private int ipLimitPerMinute;

    @Value("${security.login.refresh-limit-per-minute:30}")
    private int refreshLimitPerMinute;

    @Value("${security.login.username-limit-per-minute:10}")
    private int usernameLimitPerMinute;

    /** IP + per-username limits before password check (works for unknown usernames too). */
    @Transactional
    public void ensureLoginAttemptAllowed(String username, String ipAddress) {
        checkIpLimit("login:" + normalizeIp(ipAddress), ipLimitPerMinute);
        checkIpLimit("login-user:" + normalizeUsername(username), usernameLimitPerMinute);
    }

    @Transactional
    public void ensureAccountNotLocked(AuthUser user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new TooManyRequestsException("Account temporarily locked. Try again later.");
        }
    }

    @Transactional
    public void ensureRefreshAllowed(String ipAddress) {
        checkIpLimit("refresh:" + normalizeIp(ipAddress), refreshLimitPerMinute);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginFailure(AuthUser user) {
        AuthUser managed = authUserRepository.findById(user.getId()).orElse(user);
        int attempts = managed.getFailedLoginAttempts() + 1;
        managed.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailures) {
            managed.setLockedUntil(Instant.now().plusSeconds(lockoutMinutes * 60L));
        }
        authUserRepository.save(managed);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLoginSuccess(AuthUser user) {
        AuthUser managed = authUserRepository.findById(user.getId()).orElse(user);
        managed.setFailedLoginAttempts(0);
        managed.setLockedUntil(null);
        authUserRepository.save(managed);
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

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return "unknown";
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}
