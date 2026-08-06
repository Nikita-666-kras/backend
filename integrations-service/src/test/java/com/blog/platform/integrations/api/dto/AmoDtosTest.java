package com.blog.platform.integrations.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmoDtosTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void widgetRequest_mapsReturnUrlFromSnakeCase() throws Exception {
        String json = """
                {
                  "token": "t1",
                  "return_url": "https://example.amocrm.ru/continue/1",
                  "data": { "contact_id": "42", "phone": "+7 900 111-22-33" }
                }
                """;
        AmoDtos.WidgetRequest req = mapper.readValue(json, AmoDtos.WidgetRequest.class);
        assertEquals("https://example.amocrm.ru/continue/1", req.returnUrl());
        assertEquals("t1", req.token());
        assertNotNull(req.data());
        assertEquals("42", req.data().get("contact_id").asText());
    }
}
