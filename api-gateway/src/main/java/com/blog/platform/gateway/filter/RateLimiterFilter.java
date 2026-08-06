package com.blog.platform.gateway.filter;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Edge per-IP fixed-window limiter. Authoritative login/lockout lives in SSO (Postgres).
 * This filter is process-local — fine for a single gateway replica.
 */
@Component
@ConditionalOnProperty(name = "security.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);
    private final Map<String, Counter> requestCounters = new ConcurrentHashMap<>();

    @Value("${security.rate-limit.api-per-minute:300}")
    private int defaultLimit;

    @Value("${security.rate-limit.auth-per-minute:20}")
    private int authLimit;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isExemptFromRateLimit(path)) {
            return chain.filter(exchange);
        }

        String ip = clientIp(exchange.getRequest());
        boolean authEndpoint = path.startsWith("/auth/login")
                || path.startsWith("/auth/refresh")
                || path.startsWith("/auth/logout");
        String bucketKey = ip + (authEndpoint ? ":auth" : ":api");
        int limit = authEndpoint ? authLimit : defaultLimit;

        Counter counter = requestCounters.computeIfAbsent(bucketKey, ignored -> new Counter());
        synchronized (counter) {
            long now = Instant.now().getEpochSecond();
            if (now - counter.windowStart > 60) {
                counter.windowStart = now;
                counter.requests = 0;
            }
            counter.requests++;
            if (counter.requests > limit) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }
        log.debug("{} {}", exchange.getRequest().getMethod(), path);
        return chain.filter(exchange);
    }

    /**
     * Админка и менеджерский хаб — JWT на gateway; превью /media — десятки параллельных GET.
     * Не режем по IP-лимиту (иначе 429 при каталоге запчастей и загрузке папки).
     */
    private boolean isExemptFromRateLimit(String path) {
        if (path.startsWith("/admin/") || path.startsWith("/manager/")) {
            return true;
        }
        if (path.startsWith("/media/") || path.equals("/media")) {
            return true;
        }
        if (path.startsWith("/amocrm")) {
            return true;
        }
        return false;
    }

    @Scheduled(fixedDelayString = "${security.rate-limit.cleanup-ms:60000}")
    void cleanupStaleBuckets() {
        long cutoff = Instant.now().getEpochSecond() - 120;
        Iterator<Map.Entry<String, Counter>> it = requestCounters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Counter> entry = it.next();
            Counter counter = entry.getValue();
            synchronized (counter) {
                if (counter.windowStart < cutoff) {
                    it.remove();
                }
            }
        }
    }

    private String clientIp(ServerHttpRequest request) {
        String forwarded = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (request.getRemoteAddress() == null || request.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return request.getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -20;
    }

    private static class Counter {
        private long windowStart = Instant.now().getEpochSecond();
        private int requests = 0;
    }
}
