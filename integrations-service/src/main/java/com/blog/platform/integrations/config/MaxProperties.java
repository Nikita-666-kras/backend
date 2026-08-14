package com.blog.platform.integrations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@ConfigurationProperties(prefix = "max")
public record MaxProperties(
        String botToken,
        String ordersUserIds
) {
    public MaxProperties {
        botToken = normalizeToken(botToken);
        ordersUserIds = ordersUserIds == null ? "" : ordersUserIds.trim();
    }

    public boolean configured() {
        return !botToken.isBlank() && !userIds().isEmpty();
    }

    private static String normalizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        String token = raw.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        if (token.length() >= 2
                && ((token.startsWith("\"") && token.endsWith("\""))
                || (token.startsWith("'") && token.endsWith("'")))) {
            token = token.substring(1, token.length() - 1).trim();
        }
        return token;
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
