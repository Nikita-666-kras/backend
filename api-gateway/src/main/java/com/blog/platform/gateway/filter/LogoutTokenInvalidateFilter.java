package com.blog.platform.gateway.filter;

import com.blog.platform.gateway.security.TokenVersionCache;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * After a successful logout, refresh the token-version cache immediately
 * so access JWTs are rejected without waiting for the next poll.
 */
@Component
public class LogoutTokenInvalidateFilter implements GlobalFilter, Ordered {

    private final TokenVersionCache tokenVersionCache;

    public LogoutTokenInvalidateFilter(TokenVersionCache tokenVersionCache) {
        this.tokenVersionCache = tokenVersionCache;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean logout = HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && path.startsWith("/auth/logout");
        if (!logout) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange).then(Mono.<Void>defer(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            if (status != null && status.is2xxSuccessful()) {
                return Mono.<Void>fromRunnable(tokenVersionCache::refresh)
                        .subscribeOn(Schedulers.boundedElastic());
            }
            return Mono.empty();
        }));
    }

    @Override
    public int getOrder() {
        return 50;
    }
}
