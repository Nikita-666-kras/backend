package com.blog.platform.common.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ContentDispositionSupport {

    private ContentDispositionSupport() {
    }

    public static String inlineFilename(String originalName) {
        String safe = sanitizeFilename(originalName);
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"" + safe + "\"; filename*=UTF-8''" + encoded;
    }

    public static String sanitizeFilename(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }
        String cleaned = originalName.replaceAll("[\\r\\n\"\\\\]", "_").trim();
        if (cleaned.isBlank()) {
            return "file";
        }
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }
}
