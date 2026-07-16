package com.blog.platform.article.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugServiceTest {
    private final SlugService slugService = new SlugService();

    @Test
    void shouldBuildSlug() {
        assertThat(slugService.toSlug("Hello Spring Boot World!")).isEqualTo("hello-spring-boot-world");
    }
}
