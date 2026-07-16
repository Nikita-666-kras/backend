package com.blog.platform.article.service;

import org.springframework.stereotype.Service;

@Service
public class SlugService {
    public String toSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }
}
