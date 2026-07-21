package com.blog.platform.article.service;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Service;

@Service
public class MarkdownService {
    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownService() {
        MutableDataSet options = new MutableDataSet();
        this.parser = Parser.builder(options).build();
        // Allow trusted editor HTML (<video>, etc.) embedded in markdown content.
        this.renderer = HtmlRenderer.builder(options).escapeHtml(false).build();
    }

    public String toHtml(String markdown) {
        return renderer.render(parser.parse(markdown));
    }
}
