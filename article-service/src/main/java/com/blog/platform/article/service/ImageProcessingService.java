package com.blog.platform.article.service;

import com.blog.platform.article.config.MediaProperties;
import jakarta.annotation.PostConstruct;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingService {

    private static final Set<String> PROCESSABLE_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/tiff",
            "image/tif"
    );

    private final MediaProperties mediaProperties;

    private Path storageRoot;
    private Path resolvedLogoPath;

    @PostConstruct
    void init() throws IOException {
        storageRoot = Paths.get(mediaProperties.getStoragePath()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
        resolvedLogoPath = resolveLogoPath();
        ensureDefaultLogo();
    }

    public boolean isProcessable(String contentType) {
        return contentType != null && PROCESSABLE_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
    }

    public BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IllegalArgumentException("Не удалось прочитать изображение");
        }
        return image;
    }

    /**
     * Make square without cropping: pad to max(w,h) with background, centered.
     * Matches sharp extend() behaviour from make_square.
     */
    public BufferedImage makeSquare(BufferedImage source, Color background) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width == height) {
            return copy(source);
        }

        int size = Math.max(width, height);
        int padLeft = (size - width) / 2;
        int padRight = size - width - padLeft;
        int padTop = (size - height) / 2;
        int padBottom = size - height - padTop;

        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(background);
            graphics.fillRect(0, 0, size, size);
            graphics.drawImage(source, padLeft, padTop, null);
            // padRight / padBottom are intentional (centering remainder on right/bottom)
            if (padRight < 0 || padBottom < 0) {
                throw new IllegalStateException("Некорректный паддинг квадрата");
            }
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    /**
     * Stretch logo to full frame, key out near-black, multiply alpha by opacity, composite.
     * Matches sharp add_watermark behaviour.
     */
    public BufferedImage applyWatermark(BufferedImage source, Float opacityOverride, Integer bgThresholdOverride)
            throws IOException {
        MediaProperties.Watermark settings = mediaProperties.getWatermark();
        float opacity = opacityOverride != null ? clampOpacity(opacityOverride) : clampOpacity(settings.getOpacity());
        int bgThreshold = bgThresholdOverride != null
                ? clampThreshold(bgThresholdOverride)
                : clampThreshold(settings.getBgThreshold());

        BufferedImage logoRaw = readLogo();
        BufferedImage overlay = prepareLogoOverlay(logoRaw, source.getWidth(), source.getHeight(), opacity, bgThreshold);

        BufferedImage result = copy(source);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(overlay, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    public Color parseBackground(String raw) {
        String value = StringUtils.hasText(raw) ? raw.trim() : mediaProperties.getSquareBackground();
        if (!StringUtils.hasText(value)) {
            return Color.WHITE;
        }
        String hex = value.startsWith("#") ? value.substring(1) : value;
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) {
            throw new IllegalArgumentException("Цвет фона должен быть в формате #RRGGBB");
        }
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректный цвет фона: " + raw);
        }
    }

    public Path logoPath() {
        return resolvedLogoPath;
    }

    public void write(Path path, BufferedImage image, String contentType) throws IOException {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if ("image/png".equals(normalized)) {
            ImageIO.write(image, "png", path.toFile());
            return;
        }
        if ("image/webp".equals(normalized)) {
            writeWebp(path, image);
            return;
        }
        if ("image/gif".equals(normalized)) {
            // Prefer PNG output quality for processed GIFs while keeping .gif name is awkward;
            // write GIF best-effort via ImageIO.
            if (!ImageIO.write(image, "gif", path.toFile())) {
                ImageIO.write(image, "png", path.toFile());
            }
            return;
        }
        if ("image/tiff".equals(normalized) || "image/tif".equals(normalized)) {
            if (!ImageIO.write(image, "TIFF", path.toFile()) && !ImageIO.write(image, "tif", path.toFile())) {
                writeJpeg(path, image);
            }
            return;
        }
        writeJpeg(path, image);
    }

    private BufferedImage prepareLogoOverlay(
            BufferedImage logoRaw,
            int width,
            int height,
            float opacity,
            int bgThreshold
    ) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(logoRaw, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = scaled.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if (red <= bgThreshold && green <= bgThreshold && blue <= bgThreshold) {
                    alpha = 0;
                } else {
                    alpha = Math.round(alpha * opacity);
                }
                scaled.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        return scaled;
    }

    private BufferedImage readLogo() throws IOException {
        if (resolvedLogoPath != null && Files.isRegularFile(resolvedLogoPath)) {
            BufferedImage logo = ImageIO.read(resolvedLogoPath.toFile());
            if (logo != null) {
                return logo;
            }
        }
        ClassPathResource resource = new ClassPathResource("watermarks/watermark.png");
        if (resource.exists()) {
            try (InputStream in = resource.getInputStream()) {
                BufferedImage logo = ImageIO.read(in);
                if (logo != null) {
                    return logo;
                }
            }
        }
        throw new IllegalStateException(
                "Логотип watermark не найден. Положите PNG в " + resolvedLogoPath
                        + " или задайте MEDIA_WATERMARK_LOGO_PATH"
        );
    }

    private Path resolveLogoPath() {
        String configured = mediaProperties.getWatermark().getLogoPath();
        if (!StringUtils.hasText(configured)) {
            return storageRoot.resolve("watermark.png");
        }
        Path path = Paths.get(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return storageRoot.resolve(configured).normalize();
    }

    private void ensureDefaultLogo() throws IOException {
        if (Files.isRegularFile(resolvedLogoPath)) {
            return;
        }
        ClassPathResource resource = new ClassPathResource("watermarks/watermark.png");
        if (resource.exists()) {
            Files.createDirectories(resolvedLogoPath.getParent());
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, resolvedLogoPath);
            }
            log.info("Copied default watermark logo to {}", resolvedLogoPath);
            return;
        }
        Files.createDirectories(resolvedLogoPath.getParent());
        BufferedImage logo = createDefaultLogo(1200, 1200);
        ImageIO.write(logo, "png", resolvedLogoPath.toFile());
        log.info("Generated default watermark logo at {}", resolvedLogoPath);
    }

    /** Black background + light text — works with bgThreshold keying. */
    private BufferedImage createDefaultLogo(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int fontSize = Math.max(48, width / 8);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
            graphics.setColor(new Color(245, 245, 245));
            String text = "АТРИС";
            FontMetrics metrics = graphics.getFontMetrics();
            int x = (width - metrics.stringWidth(text)) / 2;
            int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            graphics.drawString(text, x, y);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void writeWebp(Path path, BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType("image/webp");
        if (!writers.hasNext()) {
            throw new IllegalStateException("WebP writer недоступен");
        }
        ImageWriter writer = writers.next();
        try (OutputStream output = Files.newOutputStream(path);
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = params.getCompressionTypes();
                if (types != null && types.length > 0) {
                    int lossyIndex = -1;
                    for (int i = 0; i < types.length; i++) {
                        if (types[i] != null && types[i].toLowerCase(Locale.ROOT).contains("lossy")) {
                            lossyIndex = i;
                            break;
                        }
                    }
                    params.setCompressionType(types[lossyIndex >= 0 ? lossyIndex : 0]);
                }
                try {
                    params.setCompressionQuality(0.90f);
                } catch (UnsupportedOperationException ignored) {
                    // ignore
                }
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private void writeJpeg(Path path, BufferedImage image) throws IOException {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JPEG writer недоступен");
        }
        ImageWriter writer = writers.next();
        try (OutputStream output = Files.newOutputStream(path);
                ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.92f);
            }
            writer.write(null, new IIOImage(rgb, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private float clampOpacity(float opacity) {
        if (opacity < 0.01f) {
            return 0.01f;
        }
        if (opacity > 1f) {
            return 1f;
        }
        return opacity;
    }

    private int clampThreshold(int threshold) {
        if (threshold < 0) {
            return 0;
        }
        if (threshold > 255) {
            return 255;
        }
        return threshold;
    }
}
