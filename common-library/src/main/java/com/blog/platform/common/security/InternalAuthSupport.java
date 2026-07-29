package com.blog.platform.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class InternalAuthSupport {

    public static final String TRUSTED_INTERNAL_ATTRIBUTE = "trustedInternal";

    private InternalAuthSupport() {
    }

    public static boolean isValidKey(String provided, String expected) {
        if (provided == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}
