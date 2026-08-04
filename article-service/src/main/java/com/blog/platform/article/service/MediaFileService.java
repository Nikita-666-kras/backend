package com.blog.platform.article.service;

import com.blog.platform.article.api.dto.MediaDtos.BatchProcessResponse;
import com.blog.platform.article.api.dto.MediaDtos.MediaResponse;
import com.blog.platform.article.api.dto.MediaDtos.PageResponse;
import com.blog.platform.article.api.dto.MediaDtos.ProcessingSettingsResponse;
import com.blog.platform.article.config.MediaProperties;
import com.blog.platform.article.domain.MediaFile;
import com.blog.platform.article.domain.MediaKind;
import com.blog.platform.article.domain.MediaSection;
import com.blog.platform.article.repository.MediaFileRepository;
import com.blog.platform.common.exception.NotFoundException;
import com.blog.platform.common.security.ContentDispositionSupport;
import jakarta.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaFileService {

    private final MediaFileRepository mediaFileRepository;
    private final MediaProperties mediaProperties;
    private final ImageProcessingService imageProcessingService;

    private Path storageRoot;

    @PostConstruct
    void initStorage() throws IOException {
        storageRoot = Paths.get(mediaProperties.getStoragePath()).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    @Transactional
    public MediaResponse upload(MultipartFile file, UUID uploadedBy, String sectionRaw) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }
        String contentType;
        try {
            contentType = MediaContentTypeSniffer.detectAndValidate(file);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось прочитать файл", ex);
        }
        MediaKind kind = detectKind(contentType);
        long maxBytes = kind == MediaKind.IMAGE
                ? mediaProperties.getMaxImageBytes()
                : mediaProperties.getMaxVideoBytes();
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Файл слишком большой");
        }

        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()
        );
        if (original.contains("..")) {
            throw new IllegalArgumentException("Недопустимое имя файла");
        }

        String ext = extension(original, contentType);
        String storedName = UUID.randomUUID() + ext;
        Path target = storageRoot.resolve(storedName);
        MediaSection section = parseSection(sectionRaw);

        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сохранить файл", ex);
        }

        MediaFile entity = new MediaFile();
        entity.setOriginalName(original);
        entity.setStoredName(storedName);
        entity.setContentType(contentType);
        entity.setSizeBytes(file.getSize());
        entity.setKind(kind);
        entity.setSection(section);
        entity.setUploadedBy(uploadedBy);
        entity.setSquare(false);
        entity.setWatermark(false);
        return toResponse(mediaFileRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PageResponse list(
            String kindRaw,
            String sectionRaw,
            String q,
            Boolean square,
            Boolean watermark,
            Boolean incomplete,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        MediaKind kind = parseKind(kindRaw);
        MediaSection section = parseSection(sectionRaw);
        String query = q == null ? null : q.trim();

        Specification<MediaFile> spec = Specification.where(null);
        if (kind != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("kind"), kind));
        }
        if (section != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("section"), section));
        }
        if (query != null && !query.isBlank()) {
            String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, cq, cb) -> cb.like(cb.lower(root.get("originalName")), pattern));
        }
        if (square != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("square"), square));
        }
        if (watermark != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("watermark"), watermark));
        }
        if (Boolean.TRUE.equals(incomplete)) {
            spec = spec.and((root, cq, cb) -> cb.or(
                    cb.isFalse(root.get("square")),
                    cb.isFalse(root.get("watermark"))
            ));
        }

        Page<MediaFile> result = mediaFileRepository.findAll(spec, pageable);
        List<MediaResponse> content = result.getContent().stream().map(this::toResponse).toList();
        return new PageResponse(content, result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public MediaResponse getMeta(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public MediaResponse updateSection(UUID id, String sectionRaw) {
        MediaFile media = require(id);
        media.setSection(parseSection(sectionRaw));
        return toResponse(mediaFileRepository.save(media));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> stream(UUID id) {
        // UUID в URL — capability-токен: отдаём файл, если запись есть в БД.
        // Раньше требовалась привязка к PUBLISHED статье/каталогу — превью в админке
        // (<img src="/media/{id}"> без JWT) всегда получали 404 на свежие загрузки.
        MediaFile media = require(id);
        Path path = storageRoot.resolve(media.getStoredName());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Файл не найден на диске");
        }
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDispositionSupport.inlineFilename(media.getOriginalName()))
                .body(resource);
    }

    @Transactional
    public void delete(UUID id) {
        MediaFile media = require(id);
        Path path = storageRoot.resolve(media.getStoredName());
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось удалить файл", ex);
        }
        mediaFileRepository.delete(media);
    }

    @Transactional
    public MediaResponse process(
            UUID id,
            boolean square,
            boolean watermark,
            boolean convertToWebp,
            String backgroundColor,
            Float opacity,
            Integer bgThreshold
    ) {
        if (!square && !watermark && !convertToWebp) {
            throw new IllegalArgumentException("Выберите хотя бы одну операцию");
        }
        MediaFile media = requireImage(id);
        if (!imageProcessingService.isProcessable(media.getContentType())) {
            throw new IllegalArgumentException("Обработка доступна для JPEG, PNG, WebP, GIF и TIFF");
        }

        Path path = storageRoot.resolve(media.getStoredName());
        try {
            BufferedImage image = imageProcessingService.read(path);
            if (square) {
                image = imageProcessingService.makeSquare(image, imageProcessingService.parseBackground(backgroundColor));
                media.setSquare(true);
            }
            if (watermark) {
                image = imageProcessingService.applyWatermark(image, opacity, bgThreshold);
                media.setWatermark(true);
            }

            if (convertToWebp) {
                path = convertStoredFileToWebp(media, path, image);
            } else {
                imageProcessingService.write(path, image, media.getContentType());
            }
            media.setSizeBytes(Files.size(path));
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось обработать изображение", ex);
        }
        return toResponse(mediaFileRepository.save(media));
    }

    @Transactional
    public BatchProcessResponse processBatch(
            List<UUID> ids,
            boolean square,
            boolean watermark,
            boolean convertToWebp,
            String backgroundColor,
            Float opacity,
            Integer bgThreshold
    ) {
        if (!square && !watermark && !convertToWebp) {
            throw new IllegalArgumentException("Выберите хотя бы одну операцию");
        }
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Не выбраны файлы");
        }

        int processed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (UUID id : ids.stream().distinct().collect(Collectors.toList())) {
            try {
                process(id, square, watermark, convertToWebp, backgroundColor, opacity, bgThreshold);
                processed++;
            } catch (Exception ex) {
                failed++;
                errors.add(id + ": " + ex.getMessage());
            }
        }
        return new BatchProcessResponse(processed, failed, errors);
    }

    private Path convertStoredFileToWebp(MediaFile media, Path currentPath, BufferedImage image) throws IOException {
        String newStoredName = replaceExtension(media.getStoredName(), ".webp");
        Path target = storageRoot.resolve(newStoredName);
        imageProcessingService.write(target, image, "image/webp");
        if (!currentPath.equals(target)) {
            Files.deleteIfExists(currentPath);
        }
        media.setStoredName(newStoredName);
        media.setContentType("image/webp");
        media.setOriginalName(replaceExtension(media.getOriginalName(), ".webp"));
        return target;
    }

    private String replaceExtension(String name, String extension) {
        if (name == null || name.isBlank()) {
            return UUID.randomUUID() + extension;
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = name;
        String prefix = "";
        if (slash >= 0) {
            prefix = name.substring(0, slash + 1);
            base = name.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        return prefix + stem + extension;
    }

    @Transactional(readOnly = true)
    public ProcessingSettingsResponse processingSettings() {
        MediaProperties.Watermark watermark = mediaProperties.getWatermark();
        Path logo = imageProcessingService.logoPath();
        return new ProcessingSettingsResponse(
                mediaProperties.getSquareBackground(),
                logo != null ? logo.toString() : watermark.getLogoPath(),
                watermark.getOpacity(),
                watermark.getBgThreshold(),
                logo != null && java.nio.file.Files.isRegularFile(logo)
        );
    }

    private MediaFile require(UUID id) {
        return mediaFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Медиафайл не найден"));
    }

    private MediaFile requireImage(UUID id) {
        MediaFile media = require(id);
        if (media.getKind() != MediaKind.IMAGE) {
            throw new IllegalArgumentException("Обработка доступна только для изображений");
        }
        return media;
    }

    private MediaResponse toResponse(MediaFile media) {
        return new MediaResponse(
                media.getId(),
                media.getOriginalName(),
                media.getContentType(),
                media.getSizeBytes(),
                media.getKind(),
                media.getSection(),
                "/media/" + media.getId(),
                media.getUploadedBy(),
                media.getCreatedAt(),
                media.getUpdatedAt(),
                media.isSquare(),
                media.isWatermark()
        );
    }

    private MediaKind detectKind(String contentType) {
        if (MediaContentTypeSniffer.isAllowedImage(contentType)) {
            return MediaKind.IMAGE;
        }
        if (MediaContentTypeSniffer.isAllowedVideo(contentType)) {
            return MediaKind.VIDEO;
        }
        throw new IllegalArgumentException("Неподдерживаемый тип файла: " + contentType);
    }

    private String normalizeContentType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Неизвестный тип файла");
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    private MediaKind parseKind(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MediaKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Неизвестный тип медиа: " + raw);
        }
    }

    private MediaSection parseSection(String raw) {
        if (raw == null || raw.isBlank()) {
            return MediaSection.OTHER;
        }
        try {
            return MediaSection.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Неизвестный раздел медиатеки: " + raw);
        }
    }

    private String extension(String original, String contentType) {
        int dot = original.lastIndexOf('.');
        if (dot > 0 && dot < original.length() - 1) {
            return original.substring(dot);
        }
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            default -> "";
        };
    }
}
