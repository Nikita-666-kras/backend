package com.blog.platform.article.config;

import com.blog.platform.common.security.InternalHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ArticleClientConfig {

    @Bean
    RestClient partsCatalogRestClient(
            @Value("${parts-service.base-url:http://parts-service:9006}") String baseUrl,
            @Value("${security.internal-api-keys.parts:${PARTS_INTERNAL_API_KEY:${INTERNAL_API_KEY}}}") String internalApiKey
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(InternalHeaders.API_KEY, internalApiKey)
                .build();
    }
}
