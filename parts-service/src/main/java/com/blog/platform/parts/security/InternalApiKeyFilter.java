package com.blog.platform.parts.security;

import com.blog.platform.common.security.InternalAuthSupport;
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
        return request.getRequestURI().startsWith("/application/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String key = request.getHeader(InternalHeaders.API_KEY);
        boolean trusted = InternalAuthSupport.isValidKey(key, internalApiKey);
        request.setAttribute(InternalAuthSupport.TRUSTED_INTERNAL_ATTRIBUTE, trusted);

        if (trusted || allowsUntrustedAccess(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean allowsUntrustedAccess(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path.startsWith("/parts/by-id/")
                || path.startsWith("/kits/by-id/")
                || path.startsWith("/drones/by-id/")
                || path.startsWith("/catalog/media/")) {
            return false;
        }
        return path.startsWith("/parts")
                || path.startsWith("/kits")
                || path.startsWith("/drones")
                || path.startsWith("/part-categories");
    }
}
