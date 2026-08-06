package com.blog.platform.integrations.service;

import com.blog.platform.integrations.api.dto.AmoDtos.AutoArResult;
import com.blog.platform.integrations.api.dto.AmoDtos.ContinuePayload;
import com.blog.platform.integrations.api.dto.AmoDtos.WidgetRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AutoArService {

    private static final Logger log = LoggerFactory.getLogger(AutoArService.class);

    private final RestClient.Builder restClientBuilder;
    private final long defaultArFieldId;
    private final String mode;

    public AutoArService(
            RestClient.Builder restClientBuilder,
            @Value("${amocrm.ar-field-id:1853459}") long defaultArFieldId,
            @Value("${amocrm.autoar-mode:handlers}") String mode
    ) {
        this.restClientBuilder = restClientBuilder;
        this.defaultArFieldId = defaultArFieldId;
        this.mode = mode == null ? "handlers" : mode.trim().toLowerCase();
    }

    public AutoArResult process(WidgetRequest request) {
        JsonNode data = request.data();
        String phone = text(data, "phone");
        String ar = extractAr(phone);
        long arFieldId = resolveArFieldId(data);
        String status = ar.isEmpty() ? "fail" : "success";

        if (request.return_url() == null || request.return_url().isBlank()) {
            log.warn("autoar: missing return_url; contact_id={}", text(data, "contact_id"));
            return new AutoArResult(ar, status, arFieldId);
        }

        ContinuePayload payload = buildContinuePayload(ar, status, arFieldId);
        postContinue(request.return_url(), request.token(), payload);
        return new AutoArResult(ar, status, arFieldId);
    }

    static String extractAr(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (digits.length() <= 4) {
            return digits;
        }
        return digits.substring(digits.length() - 4);
    }

    private long resolveArFieldId(JsonNode data) {
        String raw = text(data, "ar_field_id");
        if (raw == null || raw.isBlank()) {
            return defaultArFieldId;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("autoar: invalid ar_field_id={}, using default {}", raw, defaultArFieldId);
            return defaultArFieldId;
        }
    }

    private ContinuePayload buildContinuePayload(String ar, String status, long arFieldId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ar", ar);
        data.put("status", status);

        if ("data_only".equals(mode)) {
            return new ContinuePayload(data, null);
        }

        Map<String, Object> actionParams = new LinkedHashMap<>();
        actionParams.put("name", "set_custom_fields");
        Map<String, Object> fieldParams = new LinkedHashMap<>();
        fieldParams.put("type", 1);
        fieldParams.put("value", ar);
        fieldParams.put("custom_fields_id", arFieldId);
        actionParams.put("params", fieldParams);

        Map<String, Object> handler = new LinkedHashMap<>();
        handler.put("handler", "action");
        handler.put("params", actionParams);

        return new ContinuePayload(data, List.of(handler));
    }

    private void postContinue(String returnUrl, String token, ContinuePayload payload) {
        try {
            RestClient.RequestBodySpec spec = restClientBuilder.build()
                    .post()
                    .uri(returnUrl)
                    .contentType(MediaType.APPLICATION_JSON);
            if (token != null && !token.isBlank()) {
                spec = spec.header("Authorization", "Bearer " + token);
            }
            spec.body(payload).retrieve().toBodilessEntity();
            log.info("autoar: continue ok ar={} status={}", payload.data().get("ar"), payload.data().get("status"));
        } catch (Exception ex) {
            log.error("autoar: continue failed url={}: {}", returnUrl, ex.getMessage());
        }
    }

    private static String text(JsonNode data, String field) {
        if (data == null || data.isNull() || !data.has(field) || data.get(field).isNull()) {
            return null;
        }
        JsonNode node = data.get(field);
        if (node.isTextual() || node.isNumber()) {
            return node.asText();
        }
        return node.toString();
    }
}
