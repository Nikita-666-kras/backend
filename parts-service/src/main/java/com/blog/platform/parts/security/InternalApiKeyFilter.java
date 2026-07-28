package com.blog.platform.parts.security;

import com.blog.platform.common.security.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    @Value("${security.internal-api-key}")
    private String internalApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/application/")) {
            return true;
        }
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        if (path.startsWith("/parts/by-id/")
                || path.startsWith("/kits/by-id/")
                || path.startsWith("/drones/by-id/")) {
            return false;
        }
        return path.startsWith("/parts")
                || path.startsWith("/kits")
                || path.startsWith("/drones")
                || path.startsWith("/part-categories");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = request.getHeader(InternalHeaders.API_KEY);
        if (key == null || !key.equals(internalApiKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
