package com.blog.platform.article.service;

import com.blog.platform.article.api.dto.ArticleDto;
import com.blog.platform.article.domain.Article;
import com.blog.platform.article.domain.ArticleStatus;
import com.blog.platform.article.repository.ArticleRepository;
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
    private final SlugService slugService;
    private final MarkdownService markdownService;

    @Transactional
    public Article create(ArticleDto.Create request) {
        Article article = new Article();
        article.setTitle(request.title());
        article.setSlug(slugService.toSlug(request.title()) + "-" + System.currentTimeMillis());
        article.setShortDescription(request.shortDescription());
        article.setContent(request.content());
        article.setHtmlContent(markdownService.toHtml(request.content()));
        article.setAuthorId(request.authorId());
        article.setMediaObjectNames(toMutableSet(request.mediaObjectNames()));
        article.setTags(toMutableSet(request.tags()));
        article.setCategories(toMutableSet(request.categories()));
        article.setReadingTime(Math.max(1, request.content().split("\\s+").length / 200));
        return articleRepository.save(article);
    }

    @Transactional(readOnly = true)
    public Article getBySlug(String slug) {
        Article article = articleRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Article not found"));
        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            throw new IllegalArgumentException("Article not found");
        }
        return article;
    }

    @Transactional(readOnly = true)
    public Article getById(UUID id) {
        return articleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Article not found"));
    }

    @Transactional
    public Article updateStatus(UUID id, ArticleStatus status) {
        Article article = getById(id);
        if (status == ArticleStatus.PUBLISHED) {
            article.setHtmlContent(markdownService.toHtml(article.getContent()));
        }
        article.setStatus(status);
        return articleRepository.save(article);
    }

    @Transactional(readOnly = true)
    public Page<Article> search(String q, UUID authorId, String tag, String category, ArticleStatus status, Pageable pageable) {
        Specification<Article> spec = Specification.where(ArticleSpecifications.byTitleLike(q))
                .and(ArticleSpecifications.byAuthor(authorId))
                .and(ArticleSpecifications.byTag(tag))
                .and(ArticleSpecifications.byCategory(category))
                .and(ArticleSpecifications.byStatus(status));
        return articleRepository.findAll(spec, pageable);
    }

    @Transactional
    public Article update(UUID id, ArticleDto.Create request) {
        Article article = articleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Article not found"));
        article.setTitle(request.title());
        article.setShortDescription(request.shortDescription());
        article.setContent(request.content());
        article.setHtmlContent(markdownService.toHtml(request.content()));
        article.setAuthorId(request.authorId());
        article.setMediaObjectNames(toMutableSet(request.mediaObjectNames()));
        article.setTags(toMutableSet(request.tags()));
        article.setCategories(toMutableSet(request.categories()));
        article.setReadingTime(Math.max(1, request.content().split("\\s+").length / 200));
        return articleRepository.save(article);
    }

    @Transactional
    public void delete(UUID id) {
        if (!articleRepository.existsById(id)) {
            throw new IllegalArgumentException("Article not found");
        }
        articleRepository.deleteById(id);
    }

    private java.util.Set<String> toMutableSet(java.util.Set<String> source) {
        return source == null ? new java.util.HashSet<>() : new java.util.HashSet<>(source);
    }
}
