package com.blog.platform.integrations.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public final class AmoDtos {

    private AmoDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WidgetRequest(
            String token,
            JsonNode data,
            String return_url
    ) {
    }

    public record AutoArResult(String ar, String status, long arFieldId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContinuePayload(
            Map<String, Object> data,
            Object execute_handlers
    ) {
    }
}
