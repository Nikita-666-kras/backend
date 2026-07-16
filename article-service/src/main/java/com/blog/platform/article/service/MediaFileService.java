package com.blog.platform.article.service;

import com.blog.platform.article.api.dto.MediaDtos.MediaResponse;
import com.blog.platform.article.api.dto.MediaDtos.PageResponse;
import com.blog.platform.article.domain.Article;
import com.blog.platform.article.domain.MediaFile;
import com.blog.platform.article.domain.MediaKind;
import com.blog.platform.article.repository.ArticleRepository;
import com.blog.platform.article.repository.MediaFileRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MediaFileService {

    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/webm");
    private static final Map<String, String> EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif",
            "video/mp4", "mp4",
            "video/webm", "webm"
    );

    private final MediaFileRepository mediaFileRepository;
    private final ArticleRepository articleRepository;

    @Value("${media.storage-path:/data/media}")
    private String storagePath;

    @Value("${media.max-image-bytes:10485760}")
    private long maxImageBytes;

    @Value("${media.max-video-bytes:104857600}")
    private long maxVideoBytes;

    @Transactional
    public MediaResponse upload(MultipartFile file, UUID uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        MediaKind kind = resolveKind(contentType);
        long max = kind == MediaKind.IMAGE ? maxImageBytes : maxVideoBytes;
        if (file.getSize() > max) {
            throw new IllegalArgumentException("Файл слишком большой для типа " + kind);
        }
        String ext = EXT.get(contentType);
        String storedName = UUID.randomUUID() + "." + ext;
        Path dir = Path.of(storagePath);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сохранить файл", ex);
        }

        MediaFile media = new MediaFile();
        media.setOriginalName(safeName(file.getOriginalFilename()));
        media.setStoredName(storedName);
        media.setContentType(contentType);
        media.setSizeBytes(file.getSize());
        media.setKind(kind);
        media.setUploadedBy(uploadedBy);
        return toResponse(mediaFileRepository.save(media));
    }

    @Transactional(readOnly = true)
    public PageResponse list(String kind, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean hasQ = q != null && !q.isBlank();
        MediaKind mediaKind = parseKind(kind);
        Page<MediaFile> result;
        if (mediaKind != null && hasQ) {
            result = mediaFileRepository.findByKindAndOriginalNameContainingIgnoreCase(mediaKind, q.trim(), pageable);
        } else if (mediaKind != null) {
            result = mediaFileRepository.findByKind(mediaKind, pageable);
        } else if (hasQ) {
            result = mediaFileRepository.findByOriginalNameContainingIgnoreCase(q.trim(), pageable);
        } else {
            result = mediaFileRepository.findAll(pageable);
        }
        return new PageResponse(
                result.map(this::toResponse).getContent(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public MediaResponse getMeta(UUID id) {
        return toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> stream(UUID id) {
        MediaFile media = require(id);
        Path path = Path.of(storagePath).resolve(media.getStoredName());
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Файл не найден на диске");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(MediaType.parseMediaType(media.getContentType()))
                .contentLength(media.getSizeBytes())
                .body(new FileSystemResource(path));
    }

    @Transactional
    public void delete(UUID id) {
        MediaFile media = require(id);
        for (Article article : articleRepository.findAll()) {
            boolean changed = false;
            if (id.equals(article.getCoverMediaId())) {
                article.setCoverMediaId(null);
                changed = true;
            }
            if (article.getMediaObjectNames() != null && article.getMediaObjectNames().remove(id.toString())) {
                changed = true;
            }
            if (changed) {
                articleRepository.save(article);
            }
        }
        Path path = Path.of(storagePath).resolve(media.getStoredName());
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // metadata removal is still performed
        }
        mediaFileRepository.delete(media);
    }

    public MediaResponse toResponse(MediaFile media) {
        return new MediaResponse(
                media.getId(),
                media.getOriginalName(),
                media.getContentType(),
                media.getSizeBytes(),
                media.getKind(),
                "/media/" + media.getId(),
                media.getUploadedBy(),
                media.getCreatedAt()
        );
    }

    private MediaFile require(UUID id) {
        return mediaFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Файл не найден"));
    }

    private MediaKind resolveKind(String contentType) {
        if (IMAGE_TYPES.contains(contentType)) {
            return MediaKind.IMAGE;
        }
        if (VIDEO_TYPES.contains(contentType)) {
            return MediaKind.VIDEO;
        }
        throw new IllegalArgumentException("Разрешены jpeg/png/webp/gif и mp4/webm");
    }

    private MediaKind parseKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        return MediaKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
    }

    private String normalizeContentType(String contentType, String filename) {
        if (contentType != null && !contentType.isBlank() && !contentType.equals("application/octet-stream")) {
            return contentType.toLowerCase(Locale.ROOT);
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".mp4")) return "video/mp4";
        if (name.endsWith(".webm")) return "video/webm";
        throw new IllegalArgumentException("Не удалось определить тип файла");
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        return name.replaceAll("[\\\\/]+", "_").trim();
    }
}
