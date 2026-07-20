package com.blog.platform.article.service;

import com.blog.platform.article.api.dto.ArticleDto;
import com.blog.platform.article.domain.Article;
import com.blog.platform.article.domain.ArticleStatus;
import com.blog.platform.article.domain.MediaFile;
import com.blog.platform.article.repository.ArticleRepository;
import com.blog.platform.article.repository.MediaFileRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
        apply(article, request);
        article.setSlug(slugService.toSlug(request.title()) + "-" + System.currentTimeMillis());
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
        return toDto(articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found")));
    }

    @Transactional
    public ArticleDto updateStatus(UUID id, ArticleStatus status) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));
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
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));
        apply(article, request);
        return toDto(articleRepository.save(article));
    }

    @Transactional
    public void delete(UUID id) {
        if (!articleRepository.existsById(id)) {
            throw new IllegalArgumentException("Article not found");
        }
        articleRepository.deleteById(id);
    }

    private void apply(Article article, ArticleDto.Create request) {
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
    }

    private ArticleDto toDto(Article article) {
        List<UUID> mediaIds = new ArrayList<>();
        if (article.getMediaObjectNames() != null) {
            for (String raw : article.getMediaObjectNames()) {
                try {
                    mediaIds.add(UUID.fromString(raw));
                } catch (Exception ignored) {
                    // skip invalid
                }
            }
        }
        List<MediaFile> files = mediaIds.isEmpty() ? List.of() : mediaFileRepository.findByIdIn(mediaIds);
        List<ArticleDto.MediaItem> media = files.stream()
                .map(f -> new ArticleDto.MediaItem(f.getId(), "/media/" + f.getId(), f.getKind(), f.getOriginalName()))
                .collect(Collectors.toList());
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

    private Set<String> toMutableSet(Set<String> source) {
        return source == null ? new HashSet<>() : new HashSet<>(source);
    }
}
