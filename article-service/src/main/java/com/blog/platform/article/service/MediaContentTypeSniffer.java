package com.blog.platform.article.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

public final class MediaContentTypeSniffer {

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );
    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/webm");

    private MediaContentTypeSniffer() {
    }

    public static String detectAndValidate(MultipartFile file) throws IOException {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(16);
        }
        String detected = detectFromHeader(header);
        if (detected == null) {
            throw new IllegalArgumentException("Неподдерживаемый тип файла");
        }
        String declared = normalizeDeclared(file.getContentType());
        if (declared != null && !declared.equals(detected) && !isCompatible(declared, detected)) {
            throw new IllegalArgumentException("Тип файла не совпадает с содержимым");
        }
        return detected;
    }

    public static boolean isAllowedImage(String contentType) {
        return ALLOWED_IMAGE_TYPES.contains(contentType);
    }

    public static boolean isAllowedVideo(String contentType) {
        return ALLOWED_VIDEO_TYPES.contains(contentType);
    }

    private static String normalizeDeclared(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private static boolean isCompatible(String declared, String detected) {
        if ("image/jpg".equals(declared) && "image/jpeg".equals(detected)) {
            return true;
        }
        return declared.equals(detected);
    }

    private static String detectFromHeader(byte[] header) {
        if (header.length >= 3 && header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (header.length >= 8
                && header[0] == (byte) 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47) {
            return "image/png";
        }
        if (header.length >= 6
                && header[0] == 0x47
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x38) {
            return "image/gif";
        }
        if (header.length >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50) {
            return "image/webp";
        }
        if (header.length >= 8
                && header[0] == 0x00
                && header[1] == 0x00
                && header[2] == 0x00
                && (header[3] == 0x18 || header[3] == 0x20)
                && header[4] == 0x66
                && header[5] == 0x74
                && header[6] == 0x79
                && header[7] == 0x70) {
            return "video/mp4";
        }
        if (header.length >= 4
                && header[0] == 0x1A
                && header[1] == 0x45
                && header[2] == (byte) 0xDF
                && header[3] == (byte) 0xA3) {
            return "video/webm";
        }
        return null;
    }
}
