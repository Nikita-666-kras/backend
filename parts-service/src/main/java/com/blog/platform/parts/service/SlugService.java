package com.blog.platform.parts.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class SlugService {

    public String uniqueSlug(String source, Predicate<String> exists) {
        String base = slugify(source);
        if (base.isBlank()) {
            base = "item";
        }
        String candidate = base;
        int i = 2;
        while (exists.test(candidate)) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    public String slugify(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9а-яё]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized;
    }
}
