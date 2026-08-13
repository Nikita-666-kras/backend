package com.blog.platform.integrations.service;

import com.blog.platform.integrations.config.AmoCrmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class AmoCrmApiClient {

    private static final Logger log = LoggerFactory.getLogger(AmoCrmApiClient.class);

    private final RestClient restClient;
    private final AmoCrmProperties properties;

    public AmoCrmApiClient(RestClient.Builder restClientBuilder, AmoCrmProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    Optional<Long> findMainContactId(long leadId) {
        JsonNode root = get("/api/v4/leads/" + leadId + "?with=contacts,companies");
        if (root == null) {
            return Optional.empty();
        }
        JsonNode embedded = root.path("_embedded");
        JsonNode contacts = embedded.path("contacts");
        if (!contacts.isArray() || contacts.isEmpty()) {
            log.warn("amo api: lead {} has no linked contacts", leadId);
            return Optional.empty();
        }
        for (JsonNode link : contacts) {
            if (link.path("is_main").asBoolean(false)) {
                return Optional.of(link.path("id").asLong());
            }
        }
        return Optional.of(contacts.get(0).path("id").asLong());
    }

    Optional<String> fetchContactPhone(long contactId) {
        JsonNode root = get("/api/v4/contacts/" + contactId);
        if (root == null) {
            return Optional.empty();
        }
        return extractPhone(root);
    }

    Optional<ContactSnapshot> fetchContactSnapshot(long contactId) {
        JsonNode root = get("/api/v4/contacts/" + contactId);
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(new ContactSnapshot(
                text(root, "name"),
                extractPhone(root).orElse("")
        ));
    }

    Optional<String> fetchCompanyName(long leadId) {
        JsonNode root = get("/api/v4/leads/" + leadId + "?with=companies");
        if (root == null) {
            return Optional.empty();
        }
        JsonNode companies = root.path("_embedded").path("companies");
        if (!companies.isArray() || companies.isEmpty()) {
            return Optional.empty();
        }
        long companyId = companies.get(0).path("id").asLong();
        JsonNode company = get("/api/v4/companies/" + companyId);
        if (company == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(text(company, "name")).filter(s -> !s.isBlank());
    }

    Optional<String> fetchLeadCustomField(long leadId, long fieldId) {
        JsonNode root = get("/api/v4/leads/" + leadId);
        if (root == null) {
            return Optional.empty();
        }
        return findCustomFieldValue(root.path("custom_fields_values"), fieldId);
    }

    Optional<String> fetchContactCustomField(long contactId, long fieldId) {
        JsonNode root = get("/api/v4/contacts/" + contactId);
        if (root == null) {
            return Optional.empty();
        }
        return findCustomFieldValue(root.path("custom_fields_values"), fieldId);
    }

    public boolean patchContactArField(long contactId, long arFieldId, String ar) {
        Map<String, Object> body = Map.of(
                "custom_fields_values", List.of(Map.of(
                        "field_id", arFieldId,
                        "values", List.of(Map.of("value", ar))
                ))
        );
        return patch("/api/v4/contacts/" + contactId, body);
    }

    boolean patchLeadArField(long leadId, long arFieldId, String ar) {
        Map<String, Object> body = Map.of(
                "custom_fields_values", List.of(Map.of(
                        "field_id", arFieldId,
                        "values", List.of(Map.of("value", ar))
                ))
        );
        return patch("/api/v4/leads/" + leadId, body);
    }

    boolean patchLeadNameAndAr(long leadId, String name, long arFieldId, String ar) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("custom_fields_values", List.of(Map.of(
                "field_id", arFieldId,
                "values", List.of(Map.of("value", ar))
        )));
        return patch("/api/v4/leads/" + leadId, body);
    }

    boolean patchLeadName(long leadId, String name) {
        return patch("/api/v4/leads/" + leadId, Map.of("name", name));
    }

    public Optional<Long> findOrCreateContact(String name, String phoneDigits) {
        Optional<Long> existing = findContactIdByPhoneQuery(phoneDigits);
        if (existing.isPresent()) {
            return existing;
        }
        return createContact(name, phoneDigits);
    }

    public Optional<Long> createLead(
            String name,
            Long pipelineId,
            Long statusId,
            List<CustomFieldValue> customFields,
            List<String> tags
    ) {
        Map<String, Object> lead = new LinkedHashMap<>();
        lead.put("name", name);
        if (pipelineId != null && pipelineId > 0) {
            lead.put("pipeline_id", pipelineId);
        }
        if (statusId != null && statusId > 0) {
            lead.put("status_id", statusId);
        }
        if (customFields != null && !customFields.isEmpty()) {
            lead.put("custom_fields_values", customFields.stream().map(this::toFieldMap).toList());
        }
        if (tags != null && !tags.isEmpty()) {
            lead.put("_embedded", Map.of(
                    "tags", tags.stream().map(tag -> Map.of("name", tag)).toList()
            ));
        }
        JsonNode response = post("/api/v4/leads", List.of(lead));
        return extractEmbeddedId(response, "leads");
    }

    public boolean linkContactToLead(long leadId, long contactId) {
        List<Map<String, Object>> body = List.of(Map.of(
                "to_entity_id", contactId,
                "to_entity_type", "contacts",
                "metadata", Map.of("is_main", true)
        ));
        JsonNode response = post("/api/v4/leads/" + leadId + "/link", body);
        return response != null;
    }

    public boolean addLeadNote(long leadId, String text) {
        Map<String, Object> note = Map.of(
                "entity_id", leadId,
                "note_type", "common",
                "params", Map.of("text", text)
        );
        JsonNode response = post("/api/v4/leads/notes", List.of(note));
        return response != null;
    }

    public record CustomFieldValue(long fieldId, String value) {
    }

    private Optional<Long> findContactIdByPhoneQuery(String phoneDigits) {
        if (phoneDigits == null || phoneDigits.isBlank()) {
            return Optional.empty();
        }
        String tail = phoneDigits.length() > 10
                ? phoneDigits.substring(phoneDigits.length() - 10)
                : phoneDigits;
        JsonNode root = get("/api/v4/contacts?query=" + tail + "&limit=5");
        if (root == null) {
            return Optional.empty();
        }
        JsonNode contacts = root.path("_embedded").path("contacts");
        if (!contacts.isArray() || contacts.isEmpty()) {
            return Optional.empty();
        }
        for (JsonNode contact : contacts) {
            Optional<String> phone = extractPhone(contact);
            if (phone.isPresent() && phonesMatch(phoneDigits, phone.get())) {
                return Optional.of(contact.path("id").asLong());
            }
        }
        return Optional.of(contacts.get(0).path("id").asLong());
    }

    private Optional<Long> createContact(String name, String phoneDigits) {
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("name", name == null || name.isBlank() ? "Клиент" : name);
        contact.put("custom_fields_values", List.of(Map.of(
                "field_id", properties.phoneFieldId(),
                "values", List.of(Map.of("value", formatPhoneForAmo(phoneDigits)))
        )));
        JsonNode response = post("/api/v4/contacts", List.of(contact));
        return extractEmbeddedId(response, "contacts");
    }

    private Map<String, Object> toFieldMap(CustomFieldValue field) {
        return Map.of(
                "field_id", field.fieldId(),
                "values", List.of(Map.of("value", field.value()))
        );
    }

    private Optional<Long> extractEmbeddedId(JsonNode response, String entity) {
        if (response == null) {
            return Optional.empty();
        }
        JsonNode items = response.path("_embedded").path(entity);
        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }
        long id = items.get(0).path("id").asLong(0);
        return id > 0 ? Optional.of(id) : Optional.empty();
    }

    private static boolean phonesMatch(String leftDigits, String rightRaw) {
        String right = rightRaw.replaceAll("\\D", "");
        if (leftDigits.equals(right)) {
            return true;
        }
        if (leftDigits.length() >= 10 && right.length() >= 10) {
            return leftDigits.endsWith(right.substring(right.length() - 10))
                    || right.endsWith(leftDigits.substring(leftDigits.length() - 10));
        }
        return false;
    }

    private static String formatPhoneForAmo(String digits) {
        if (digits == null || digits.isBlank()) {
            return "";
        }
        if (digits.startsWith("7") && digits.length() == 11) {
            return "+" + digits;
        }
        if (digits.startsWith("8") && digits.length() == 11) {
            return "+7" + digits.substring(1);
        }
        return digits.startsWith("+") ? digits : "+" + digits;
    }

    private JsonNode post(String path, Object body) {
        if (!properties.apiConfigured()) {
            log.error("amo api: not configured (AMOCRM_BASE_URL / AMOCRM_ACCESS_TOKEN)");
            return null;
        }
        try {
            return restClient.post()
                    .uri(properties.normalizedBaseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.error("amo api POST {} failed: {}", path, ex.getMessage());
            return null;
        }
    }

    private JsonNode get(String path) {
        if (!properties.apiConfigured()) {
            log.error("amo api: not configured (AMOCRM_BASE_URL / AMOCRM_ACCESS_TOKEN)");
            return null;
        }
        try {
            return restClient.get()
                    .uri(properties.normalizedBaseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.error("amo api GET {} failed: {}", path, ex.getMessage());
            return null;
        }
    }

    private boolean patch(String path, Object body) {
        if (!properties.apiConfigured()) {
            return false;
        }
        try {
            restClient.patch()
                    .uri(properties.normalizedBaseUrl() + path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.error("amo api PATCH {} failed: {}", path, ex.getMessage());
            return false;
        }
    }

    private Optional<String> extractPhone(JsonNode contact) {
        Optional<String> byFieldId = findCustomFieldValue(
                contact.path("custom_fields_values"),
                properties.phoneFieldId()
        );
        if (byFieldId.isPresent() && !byFieldId.get().isBlank()) {
            return byFieldId;
        }
        JsonNode fields = contact.path("custom_fields_values");
        if (fields.isArray()) {
            for (JsonNode field : fields) {
                if ("PHONE".equalsIgnoreCase(field.path("field_code").asText())) {
                    Optional<String> phone = firstFieldValue(field.path("values"));
                    if (phone.isPresent() && !phone.get().isBlank()) {
                        return phone;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> findCustomFieldValue(JsonNode fields, long fieldId) {
        if (!fields.isArray()) {
            return Optional.empty();
        }
        for (JsonNode field : fields) {
            if (field.path("field_id").asLong() == fieldId) {
                return firstFieldValue(field.path("values"));
            }
        }
        return Optional.empty();
    }

    private Optional<String> firstFieldValue(JsonNode values) {
        if (!values.isArray() || values.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = values.get(0);
        String value = first.path("value").asText(null);
        if (value == null || value.isBlank()) {
            value = first.asText(null);
        }
        return Optional.ofNullable(value).filter(v -> !v.isBlank());
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode()) {
            return "";
        }
        return node.path(field).asText("");
    }

    record ContactSnapshot(String name, String phone) {}
}
