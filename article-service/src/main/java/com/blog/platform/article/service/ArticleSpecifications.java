package com.blog.platform.article.service;

import com.blog.platform.article.domain.Article;
import com.blog.platform.article.domain.ArticleStatus;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class ArticleSpecifications {
    private ArticleSpecifications() {}

    public static Specification<Article> byAuthor(UUID authorId) {
        return (root, query, cb) -> authorId == null ? cb.conjunction() : cb.equal(root.get("authorId"), authorId);
    }

    public static Specification<Article> byStatus(ArticleStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    public static Specification<Article> byTitleLike(String text) {
        return (root, query, cb) -> (text == null || text.isBlank())
                ? cb.conjunction()
                : cb.like(cb.lower(root.get("title")), "%" + text.toLowerCase() + "%");
    }

    public static Specification<Article> byTag(String tag) {
        return (root, query, cb) -> (tag == null || tag.isBlank())
                ? cb.conjunction()
                : cb.isMember(tag, root.get("tags"));
    }

    public static Specification<Article> byCategory(String category) {
        return (root, query, cb) -> (category == null || category.isBlank())
                ? cb.conjunction()
                : cb.isMember(category, root.get("categories"));
    }
}
