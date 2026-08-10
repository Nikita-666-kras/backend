package com.blog.platform.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amocrm")
public record AmoCrmProperties(
        String baseUrl,
        String accessToken,
        long arFieldId,
        long phoneFieldId,
        String webhookSecret,
        boolean pipelineEnabled
) {
    public boolean apiConfigured() {
        return baseUrl != null && !baseUrl.isBlank()
                && accessToken != null && !accessToken.isBlank();
    }

    public String normalizedBaseUrl() {
        if (baseUrl == null) {
            return "";
        }
        String url = baseUrl.trim();
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
