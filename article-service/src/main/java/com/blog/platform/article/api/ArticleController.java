package com.blog.platform.article.api;

import com.blog.platform.article.api.dto.ArticleDto;
import com.blog.platform.article.domain.Article;
import com.blog.platform.article.domain.ArticleStatus;
import com.blog.platform.article.service.ArticleService;
import com.blog.platform.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping
    public ResponseEntity<ApiResponse<Article>> create(@Valid @RequestBody ArticleDto.Create request) {
        return ResponseEntity.ok(ApiResponse.of(articleService.create(request)));
    }

    @GetMapping("/by-id/{id}")
    public ResponseEntity<ApiResponse<Article>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(articleService.getById(id)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<Article>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.of(articleService.getBySlug(slug)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Article>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ArticleDto.StatusUpdate request
    ) {
        return ResponseEntity.ok(ApiResponse.of(articleService.updateStatus(id, request.status())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Article>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ArticleStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.of(articleService.search(q, authorId, tag, category, status, pageable)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Article>> update(@PathVariable UUID id, @Valid @RequestBody ArticleDto.Create request) {
        return ResponseEntity.ok(ApiResponse.of(articleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        articleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
