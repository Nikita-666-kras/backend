package com.blog.platform.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "max")
public record MaxProperties(
        String botToken,
        String ordersUserIds
) {
    public boolean configured() {
        return botToken != null && !botToken.isBlank() && !userIds().isEmpty();
    }

    public List<Long> userIds() {
        if (ordersUserIds == null || ordersUserIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(ordersUserIds.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .toList();
    }
}
