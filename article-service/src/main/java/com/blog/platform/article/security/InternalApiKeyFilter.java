package com.blog.platform.article.security;

import com.blog.platform.common.security.InternalHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final Pattern PUBLIC_MEDIA_FILE = Pattern.compile("^/media/[0-9a-fA-F-]{36}$");

    @Value("${security.internal-api-key}")
    private String internalApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/application/")) {
            return true;
        }
        if (HttpMethod.GET.matches(request.getMethod()) && path.startsWith("/posts") && !path.startsWith("/posts/by-id/")) {
            return true;
        }
        return HttpMethod.GET.matches(request.getMethod()) && PUBLIC_MEDIA_FILE.matcher(path).matches();
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
