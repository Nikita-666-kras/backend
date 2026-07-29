package com.blog.platform.article.repository;

import com.blog.platform.article.domain.Article;
import com.blog.platform.article.domain.ArticleStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, UUID>, JpaSpecificationExecutor<Article> {
    Optional<Article> findBySlug(String slug);

    @Query("""
            SELECT COUNT(a) > 0 FROM Article a
            WHERE a.status = :status AND a.coverMediaId = :mediaId
            """)
    boolean existsByStatusAndCoverMediaId(@Param("status") ArticleStatus status, @Param("mediaId") UUID mediaId);

    @Query(value = """
            SELECT COUNT(*) > 0 FROM articles a
            JOIN article_media am ON am.article_id = a.id
            WHERE a.status = :status AND am.media_object_name = :mediaId
            """, nativeQuery = true)
    boolean existsPublishedArticleMedia(@Param("status") String status, @Param("mediaId") String mediaId);
}
