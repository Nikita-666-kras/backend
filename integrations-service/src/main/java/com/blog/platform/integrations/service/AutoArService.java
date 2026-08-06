package com.blog.platform.integrations.service;

import com.blog.platform.integrations.api.dto.AmoDtos.AutoArResult;
import com.blog.platform.integrations.api.dto.AmoDtos.ContinuePayload;
import com.blog.platform.integrations.api.dto.AmoDtos.WidgetRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AutoArService {

    private static final Logger log = LoggerFactory.getLogger(AutoArService.class);

    private final RestClient.Builder restClientBuilder;
    private final TaskExecutor continueExecutor;
    private final long defaultArFieldId;

    public AutoArService(
            RestClient.Builder restClientBuilder,
            @Qualifier("amocrmContinueExecutor") TaskExecutor continueExecutor,
            @Value("${amocrm.ar-field-id:1853459}") long defaultArFieldId
    ) {
        this.restClientBuilder = restClientBuilder;
        this.continueExecutor = continueExecutor;
        this.defaultArFieldId = defaultArFieldId;
    }

    public AutoArResult process(WidgetRequest request) {
        JsonNode data = request.data();
        String phone = resolvePhone(data);
        String ar = extractAr(phone);
        long arFieldId = resolveArFieldId(data);
        String status = ar.isEmpty() ? "fail" : "success";
        String contactId = text(data, "contact_id");
        String returnUrl = request.returnUrl();

        log.info(
                "autoar: contact_id={} ar={} status={} return_url={}",
                contactId,
                ar,
                status,
                returnUrl == null || returnUrl.isBlank() ? "missing" : "present"
        );

        if (returnUrl == null || returnUrl.isBlank()) {
            log.warn("autoar: missing return_url at JSON root; contact_id={}", contactId);
            return new AutoArResult(ar, status, arFieldId);
        }

        ContinuePayload payload = buildContinuePayload(ar);
        String token = request.token();
        continueExecutor.execute(() -> postContinue(returnUrl, token, payload));
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

    /** Prefer data.phone; fall back to nested value/phone shapes from amo. */
    static String resolvePhone(JsonNode data) {
        String phone = text(data, "phone");
        if (phone != null && !phone.isBlank()) {
            return phone;
        }
        if (data == null || data.isNull()) {
            return null;
        }
        JsonNode phoneNode = data.get("phone");
        if (phoneNode != null && phoneNode.isArray() && !phoneNode.isEmpty()) {
            JsonNode first = phoneNode.get(0);
            if (first.isTextual() || first.isNumber()) {
                return first.asText();
            }
            if (first.has("value")) {
                return first.get("value").asText(null);
            }
        }
        if (phoneNode != null && phoneNode.isObject() && phoneNode.has("value")) {
            return phoneNode.get("value").asText(null);
        }
        return null;
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

    /** Only {@code data.ar} — Salesbot writes the contact field from {{json.ar}}. */
    private ContinuePayload buildContinuePayload(String ar) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ar", ar);
        return new ContinuePayload(data);
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
            log.info("autoar: continue ok ar={}", payload.data().get("ar"));
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
        if (node.isArray() || node.isObject()) {
            return null;
        }
        return node.asText(null);
    }
}
