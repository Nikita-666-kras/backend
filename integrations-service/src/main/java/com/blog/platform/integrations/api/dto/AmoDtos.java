package com.blog.platform.integrations.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public final class AmoDtos {

    private AmoDtos() {
    }

    /**
     * amoCRM widget_request body. {@code return_url} is at the JSON root (not inside data).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WidgetRequest(
            String token,
            JsonNode data,
            @JsonProperty("return_url") String returnUrl
    ) {
    }

    public record AutoArResult(String ar, String status, long arFieldId) {
    }

    /** Salesbot continue: only data.ar — field write is a separate bot step via {{json.ar}}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContinuePayload(Map<String, Object> data) {
    }
}
