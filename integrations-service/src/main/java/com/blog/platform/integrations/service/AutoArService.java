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
import java.util.Map;

/**
 * amoCRM Salesbot {@code widget_request} handler.
 * <p>
 * Official contract:
 * <ul>
 *   <li>POST body: {@code token}, {@code data}, {@code return_url} (root)</li>
 *   <li>Respond HTTP 200 within ~2s</li>
 *   <li>POST {@code return_url} with {@code { "data": { ... } }} — keys available as {@code {{json.*}}}</li>
 *   <li>{@code execute_handlers} on continue supports only {@code show}/{@code goto} — not field writes</li>
 * </ul>
 *
 * @see <a href="https://www.amocrm.ru/developers/content/digital_pipeline/salesbot">Salesbot</a>
 * @see <a href="https://www.amocrm.ru/developers/content/crm_platform/widgets-api">continue API</a>
 */
@Service
public class AutoArService {

    private static final Logger log = LoggerFactory.getLogger(AutoArService.class);

    private final RestClient.Builder restClientBuilder;
    private final long defaultArFieldId;

    public AutoArService(
            RestClient.Builder restClientBuilder,
            @Value("${amocrm.ar-field-id:1902113}") long defaultArFieldId
    ) {
        this.restClientBuilder = restClientBuilder;
        this.defaultArFieldId = defaultArFieldId;
    }

    public AutoArResult process(WidgetRequest request) {
        JsonNode data = request.data();
        String phone = resolvePhone(data);
        String ar = extractAr(phone);
        long arFieldId = resolveArFieldId(data);
        String status = ar.isEmpty() ? "fail" : "success";
        String contactId = text(data, "contact_id");
        String leadId = text(data, "lead_id");
        String returnUrl = request.returnUrl();
        boolean hasToken = request.token() != null && !request.token().isBlank();

        log.info(
                "autoar: lead_id={} contact_id={} phone_len={} ar={} status={} return_url={} token={}",
                leadId,
                contactId,
                phone == null ? 0 : phone.length(),
                ar,
                status,
                returnUrl == null || returnUrl.isBlank() ? "missing" : "present",
                hasToken ? "present" : "missing"
        );

        if (returnUrl == null || returnUrl.isBlank()) {
            log.warn("autoar: missing return_url at JSON root; contact_id={}", contactId);
            return new AutoArResult(ar, status, arFieldId);
        }

        // Bot waits for continue; send data.ar + data.status for {{json.ar}} / {{json.status}}
        ContinuePayload payload = buildContinuePayload(ar, status);
        postContinue(returnUrl, request.token(), payload);
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

    /**
     * Continue payload per widgets-api: keys under {@code data} become {@code {{json.key}}}.
     * Do not put set_custom_fields into execute_handlers — only show/goto are supported there.
     */
    private ContinuePayload buildContinuePayload(String ar, String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ar", ar);
        data.put("status", status);
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
            log.info(
                    "autoar: continue ok ar={} status={}",
                    payload.data().get("ar"),
                    payload.data().get("status")
            );
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
