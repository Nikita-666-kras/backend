package com.blog.platform.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orders")
public record OrderProperties(
        boolean enabled,
        String secret,
        long pipelineId,
        long statusId,
        long utmCampaignFieldId,
        long utmReferrerFieldId,
        String tagParts,
        String tagSite
) {
    public boolean usePipeline() {
        return pipelineId > 0 && statusId > 0;
    }

    public boolean secretConfigured() {
        return secret != null && !secret.isBlank();
    }
}
