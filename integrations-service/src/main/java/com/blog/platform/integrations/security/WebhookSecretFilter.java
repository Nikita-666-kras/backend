package com.blog.platform.integrations.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Optional shared secret for Salesbot URL (?secret= or X-Webhook-Secret).
 * Skipped when AMOCRM_WEBHOOK_SECRET is empty.
 * Registered via FilterConfig (not @Component) to avoid double registration.
 */
public class WebhookSecretFilter extends OncePerRequestFilter {

    private final String expectedSecret;

    public WebhookSecretFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret == null ? "" : expectedSecret.trim();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (expectedSecret.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader("X-Webhook-Secret");
        if (provided == null || provided.isBlank()) {
            provided = request.getParameter("secret");
        }
        if (provided == null || !expectedSecret.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"invalid webhook secret\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
