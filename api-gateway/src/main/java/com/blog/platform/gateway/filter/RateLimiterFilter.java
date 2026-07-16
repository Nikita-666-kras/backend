package com.blog.platform.gateway.filter;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimiterFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);
    private static final int DEFAULT_LIMIT = 120;
    private static final int AUTH_LIMIT = 20;
    private final Map<String, Counter> requestCounters = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String path = exchange.getRequest().getPath().value();
        boolean authEndpoint = path.startsWith("/auth/login") || path.startsWith("/auth/refresh");
        String bucketKey = ip + (authEndpoint ? ":auth" : ":api");
        int limit = authEndpoint ? AUTH_LIMIT : DEFAULT_LIMIT;

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

    @Override
    public int getOrder() {
        return -20;
    }

    private static class Counter {
        private long windowStart = Instant.now().getEpochSecond();
        private int requests = 0;
    }
}
