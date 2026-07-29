package com.blog.platform.article.service;

import com.blog.platform.article.domain.ArticleStatus;
import com.blog.platform.article.repository.ArticleRepository;
import com.blog.platform.common.api.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class MediaPublicAccessService {

    private final ArticleRepository articleRepository;
    private final RestClient partsCatalogRestClient;

    public boolean isPubliclyAccessible(UUID mediaId) {
        if (mediaId == null) {
            return false;
        }
        if (articleRepository.existsByStatusAndCoverMediaId(ArticleStatus.PUBLISHED, mediaId)) {
            return true;
        }
        if (articleRepository.existsPublishedArticleMedia(ArticleStatus.PUBLISHED.name(), mediaId.toString())) {
            return true;
        }
        return isPublishedCatalogMedia(mediaId);
    }

    private boolean isPublishedCatalogMedia(UUID mediaId) {
        try {
            ApiResponse<Boolean> response = partsCatalogRestClient.get()
                    .uri("/catalog/media/{mediaId}/public", mediaId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            return response != null && Boolean.TRUE.equals(response.data());
        } catch (Exception ex) {
            return false;
        }
    }
}
