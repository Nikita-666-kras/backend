package com.blog.platform.gateway.config;

public enum GatewayMode {
    ADMIN,
    PUBLIC,
    COMBINED;

    public static GatewayMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return COMBINED;
        }
        return switch (raw.trim().toLowerCase()) {
            case "admin" -> ADMIN;
            case "public" -> PUBLIC;
            default -> COMBINED;
        };
    }

    public boolean isPublic() {
        return this == PUBLIC;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
