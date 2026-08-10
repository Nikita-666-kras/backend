package com.blog.platform.integrations.service;

import com.blog.platform.integrations.config.AmoCrmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Digital Pipeline webhook: lead stage change → fetch contact phone via API → set AR + lead name.
 * Does not use Salesbot widget_request (more reliable on the same stage as other bots).
 */
@Service
public class PipelineAutoArService {

    private static final Logger log = LoggerFactory.getLogger(PipelineAutoArService.class);
    private static final Pattern LEAD_ID_JSON = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final long CONTACT_CF_1853457 = 1853457L;
    private static final long LEAD_CF_1853353 = 1853353L;

    private final AmoCrmProperties properties;
    private final AmoCrmApiClient amoCrmApiClient;
    private final ObjectMapper objectMapper;

    public PipelineAutoArService(
            AmoCrmProperties properties,
            AmoCrmApiClient amoCrmApiClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.amoCrmApiClient = amoCrmApiClient;
        this.objectMapper = objectMapper;
    }

    public void handleWebhook(String rawBody, String contentType) {
        if (!properties.pipelineEnabled()) {
            log.warn("pipeline-autoar: disabled (set AMOCRM_PIPELINE_ENABLED=true)");
            return;
        }
        if (!properties.apiConfigured()) {
            log.error("pipeline-autoar: AMOCRM_BASE_URL and AMOCRM_ACCESS_TOKEN required");
            return;
        }

        List<Long> leadIds = parseLeadIds(rawBody, contentType);
        if (leadIds.isEmpty()) {
            log.warn("pipeline-autoar: no lead id in webhook body_len={}", rawBody == null ? 0 : rawBody.length());
            return;
        }

        // Cap burst size — Digital Pipeline can fan out; amo returns 429 if we hammer API.
        int limit = Math.min(leadIds.size(), 10);
        if (leadIds.size() > limit) {
            log.warn("pipeline-autoar: got {} leads, processing first {}", leadIds.size(), limit);
        }
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            processLead(leadIds.get(i));
        }
    }

    private void processLead(long leadId) {
        Optional<Long> contactIdOpt = amoCrmApiClient.findMainContactId(leadId);
        if (contactIdOpt.isEmpty()) {
            log.warn("pipeline-autoar: lead_id={} no contact", leadId);
            return;
        }
        long contactId = contactIdOpt.get();

        Optional<AmoCrmApiClient.ContactSnapshot> snapshotOpt = amoCrmApiClient.fetchContactSnapshot(contactId);
        if (snapshotOpt.isEmpty()) {
            log.warn("pipeline-autoar: lead_id={} contact_id={} fetch failed", leadId, contactId);
            return;
        }

        AmoCrmApiClient.ContactSnapshot snapshot = snapshotOpt.get();
        String ar = AutoArService.extractAr(snapshot.phone());
        if (ar.isEmpty()) {
            log.warn(
                    "pipeline-autoar: lead_id={} contact_id={} phone empty (field_id={})",
                    leadId,
                    contactId,
                    properties.phoneFieldId()
            );
            return;
        }

        String leadName = buildLeadName(leadId, contactId, ar, snapshot.name());
        // AR lives on contact (field 1902113); lead name is updated separately.
        boolean nameOk = amoCrmApiClient.patchLeadName(leadId, leadName);
        boolean arOk = amoCrmApiClient.patchContactArField(contactId, properties.arFieldId(), ar);

        log.info(
                "pipeline-autoar: lead_id={} contact_id={} ar={} ar_field_id={} name_patch={} ar_patch={} name={}",
                leadId,
                contactId,
                ar,
                properties.arFieldId(),
                nameOk,
                arOk,
                leadName
        );
    }

    private String buildLeadName(long leadId, long contactId, String ar, String contactName) {
        String company = amoCrmApiClient.fetchCompanyName(leadId).orElse("");
        String contactCf = amoCrmApiClient.fetchContactCustomField(contactId, CONTACT_CF_1853457).orElse("");
        String leadCf = amoCrmApiClient.fetchLeadCustomField(leadId, LEAD_CF_1853353).orElse("");

        List<String> parts = new ArrayList<>();
        parts.add("Ар " + ar);
        if (!contactName.isBlank()) {
            parts.add(contactName.trim());
        }
        if (!company.isBlank()) {
            parts.add(company.trim());
        }
        if (!contactCf.isBlank()) {
            parts.add(contactCf.trim());
        }
        if (!leadCf.isBlank()) {
            parts.add(leadCf.trim());
        }
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }

    List<Long> parseLeadIds(String rawBody, String contentType) {
        List<Long> ids = new ArrayList<>();
        if (rawBody == null || rawBody.isBlank()) {
            return ids;
        }
        String body = rawBody.trim();

        if (body.startsWith("{") || body.startsWith("[")) {
            ids.addAll(parseLeadIdsFromJson(body));
        }
        if (ids.isEmpty() && contentType != null && contentType.contains("form")) {
            ids.addAll(parseLeadIdsFromForm(body));
        }
        if (ids.isEmpty()) {
            ids.addAll(parseLeadIdsFromJson(body));
            ids.addAll(parseLeadIdsFromForm(body));
        }
        if (ids.isEmpty()) {
            Matcher m = LEAD_ID_JSON.matcher(body);
            while (m.find()) {
                ids.add(Long.parseLong(m.group(1)));
            }
        }
        return ids.stream().distinct().toList();
    }

    private List<Long> parseLeadIdsFromJson(String body) {
        List<Long> ids = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(body);
            collectLeadIds(root.path("leads").path("status"), ids);
            collectLeadIds(root.path("leads").path("add"), ids);
            collectLeadIds(root.path("leads").path("update"), ids);
            if (root.has("id") && root.path("id").isNumber()) {
                ids.add(root.path("id").asLong());
            }
        } catch (Exception ex) {
            log.debug("pipeline-autoar: JSON parse skip: {}", ex.getMessage());
        }
        return ids;
    }

    private void collectLeadIds(JsonNode array, List<Long> ids) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode item : array) {
            if (item.has("id")) {
                ids.add(item.path("id").asLong());
            }
        }
    }

    private List<Long> parseLeadIdsFromForm(String body) {
        List<Long> ids = new ArrayList<>();
        for (String part : body.split("&")) {
            if (!part.contains("leads") || !part.contains("id")) {
                continue;
            }
            String decoded = URLDecoder.decode(part, StandardCharsets.UTF_8);
            int eq = decoded.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = decoded.substring(0, eq);
            String value = decoded.substring(eq + 1);
            if (key.matches("leads\\[(status|add|update)\\]\\[\\d+\\]\\[id\\]") && value.matches("\\d+")) {
                ids.add(Long.parseLong(value));
            }
        }
        return ids;
    }
}
