package com.blog.platform.article.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "media")
public class MediaProperties {

    private String storagePath = "/data/media";
    private long maxImageBytes = 10_485_760L;
    private long maxVideoBytes = 104_857_600L;
    /** Background used when padding images to a square. */
    private String squareBackground = "#ffffff";
    private Watermark watermark = new Watermark();

    @Data
    public static class Watermark {
        /** Absolute or storage-relative path to logo PNG (black bg + light logo). */
        private String logoPath = "watermark.png";
        /** Multiplier applied to non-keyed logo pixels (0.15 = 15%). */
        private float opacity = 0.15f;
        /** RGB channel threshold: pixels darker than this become transparent. */
        private int bgThreshold = 40;
    }
}
