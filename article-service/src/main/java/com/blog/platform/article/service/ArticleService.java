package com.blog.platform.article.service;

import com.blog.platform.article.api.dto.ArticleDto;
import com.blog.platform.article.api.dto.ArticleDto.MediaItem;
import com.blog.platform.article.domain.Article;
import com.blog.platform.article.domain.ArticleStatus;
import com.blog.platform.article.domain.MediaFile;
import com.blog.platform.article.repository.ArticleRepository;
import com.blog.platform.article.repository.MediaFileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final MediaFileRepository mediaFileRepository;
    private final SlugService slugService;
    private final MarkdownService markdownService;

    @Transactional
    public ArticleDto create(ArticleDto.Create request) {
        Article article = new Article();
        article.setTitle(request.title());
        article.setSlug(slugService.toSlug(request.title()) + "-" + System.currentTimeMillis());
        article.setShortDescription(request.shortDescription());
        article.setContent(request.content());
        article.setHtmlContent(markdownService.toHtml(request.content()));
        article.setAuthorId(request.authorId());
        article.setCoverMediaId(request.coverMediaId());
        article.setMediaObjectNames(toMutableSet(request.mediaObjectNames()));
        article.setTags(toMutableSet(request.tags()));
        article.setCategories(toMutableSet(request.categories()));
        article.setReadingTime(Math.max(1, request.content().split("\\s+").length / 200));
        return toDto(articleRepository.save(article));
    }

    @Transactional(readOnly = true)
    public ArticleDto getBySlug(String slug) {
        Article article = articleRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new IllegalArgumentException("Article not found");
        }
        return toDto(article);
    }

    @Transactional(readOnly = true)
    public ArticleDto getById(UUID id) {
        return toDto(articleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Article not found")));
    }

    @Transactional
    public ArticleDto updateStatus(UUID id, ArticleStatus status) {
        Article article = articleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Article not found"));
        if (status == ArticleStatus.PUBLISHED) {
            article.setHtmlContent(markdownService.toHtml(article.getContent()));
        }
        article.setStatus(status);
        return toDto(articleRepository.save(article));
    }

    @Transactional(readOnly = true)
    public Page<ArticleDto> search(String q, UUID authorId, String tag, String category, ArticleStatus status, Pageable pageable) {
        Specification<Article> spec = Specification.where(ArticleSpecifications.byTitleLike(q))
                .and(ArticleSpecifications.byAuthor(authorId))
                .and(ArticleSpecifications.byTag(tag))
                .and(ArticleSpecifications.byCategory(category))
                .and(ArticleSpecifications.byStatus(status));
        return articleRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional
    public ArticleDto update(UUID id, ArticleDto.Create request) {
        Article article = articleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Article not found"));
        article.setTitle(request.title());
        article.setShortDescription(request.shortDescription());
        article.setContent(request.content());
        article.setHtmlContent(markdownService.toHtml(request.content()));
        article.setAuthorId(request.authorId());
        article.setCoverMediaId(request.coverMediaId());
        article.setMediaObjectNames(toMutableSet(request.mediaObjectNames()));
        article.setTags(toMutableSet(request.tags()));
        article.setCategories(toMutableSet(request.categories()));
        article.setReadingTime(Math.max(1, request.content().split("\\s+").length / 200));
        return toDto(articleRepository.save(article));
    }

    @Transactional
    public void delete(UUID id) {
        if (!articleRepository.existsById(id)) {
            throw new IllegalArgumentException("Article not found");
        }
        articleRepository.deleteById(id);
    }

    public ArticleDto toDto(Article article) {
        List<MediaItem> media = new ArrayList<>();
        if (article.getMediaObjectNames() != null) {
            for (String raw : article.getMediaObjectNames()) {
                try {
                    UUID mediaId = UUID.fromString(raw);
                    mediaFileRepository.findById(mediaId).ifPresent(file -> media.add(toItem(file)));
                } catch (IllegalArgumentException ignored) {
                    // skip invalid ids
                }
            }
        }
        String coverUrl = article.getCoverMediaId() == null ? null : "/media/" + article.getCoverMediaId();
        return new ArticleDto(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getShortDescription(),
                article.getContent(),
                article.getHtmlContent(),
                article.getStatus(),
                article.getReadingTime(),
                article.getViews(),
                article.getLikes(),
                article.getAuthorId(),
                article.getCoverMediaId(),
                coverUrl,
                article.getMediaObjectNames(),
                media,
                article.getTags(),
                article.getCategories(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }

    private MediaItem toItem(MediaFile file) {
        return new MediaItem(file.getId(), "/media/" + file.getId(), file.getKind().name(), file.getOriginalName());
    }

    private java.util.Set<String> toMutableSet(java.util.Set<String> source) {
        return source == null ? new java.util.HashSet<>() : new java.util.HashSet<>(source);
    }
}
