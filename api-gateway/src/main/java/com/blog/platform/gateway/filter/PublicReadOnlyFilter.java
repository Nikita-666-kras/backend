package com.blog.platform.gateway.filter;

import com.blog.platform.gateway.config.GatewayMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Hard edge for public-gateway: only GET/HEAD/OPTIONS; no admin/auth/manager surface.
 */
@Component
public class PublicReadOnlyFilter implements GlobalFilter, Ordered {

    private final GatewayMode mode;

    public PublicReadOnlyFilter(@Value("${gateway.mode:combined}") String mode) {
        this.mode = GatewayMode.from(mode);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!mode.isPublic()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();
        HttpMethod method = exchange.getRequest().getMethod();

        if (isBlockedAdminSurface(path)) {
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }

        // Salesbot / CRM webhooks and public order intake (server-to-server or browser POST)
        if (isPublicWrite(path, method)) {
            return chain.filter(exchange);
        }

        if (HttpMethod.OPTIONS.equals(method) || HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method)) {
            return chain.filter(exchange);
        }

        exchange.getResponse().setStatusCode(HttpStatus.METHOD_NOT_ALLOWED);
        return exchange.getResponse().setComplete();
    }

    private boolean isPublicWrite(String path, HttpMethod method) {
        if (!HttpMethod.POST.equals(method) && !HttpMethod.OPTIONS.equals(method)) {
            return false;
        }
        return path.startsWith("/amocrm") || path.startsWith("/public/orders");
    }

    private boolean isBlockedAdminSurface(String path) {
        return path.startsWith("/admin")
                || path.startsWith("/auth")
                || path.startsWith("/manager")
                || path.startsWith("/actuator")
                || (path.startsWith("/application/")
                && !"/application/health".equals(path)
                && !"/application/health/".equals(path));
    }

    @Override
    public int getOrder() {
        return -20;
    }
}
