package com.blog.platform.logging.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash log fingerprint", ex);
        }
    }
}
