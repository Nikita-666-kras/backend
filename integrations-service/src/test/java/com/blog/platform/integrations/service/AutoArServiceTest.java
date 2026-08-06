package com.blog.platform.integrations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoArServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void extractAr_takesLastFourDigits() {
        assertEquals("4567", AutoArService.extractAr("+7 (999) 123-45-67"));
        assertEquals("4567", AutoArService.extractAr("89991234567"));
        assertEquals("67", AutoArService.extractAr("67"));
        assertEquals("", AutoArService.extractAr("abc"));
        assertEquals("", AutoArService.extractAr(null));
        assertEquals("", AutoArService.extractAr("   "));
    }

    @Test
    void resolvePhone_fromPlainAndNested() throws Exception {
        ObjectNode plain = mapper.createObjectNode();
        plain.put("phone", "+7 999 111-22-33");
        assertEquals("+7 999 111-22-33", AutoArService.resolvePhone(plain));

        ObjectNode nested = mapper.createObjectNode();
        nested.set("phone", mapper.createObjectNode().put("value", "89001112233"));
        assertEquals("89001112233", AutoArService.resolvePhone(nested));

        ObjectNode arr = mapper.createObjectNode();
        arr.set("phone", mapper.createArrayNode().add("89004445566"));
        assertEquals("89004445566", AutoArService.resolvePhone(arr));
    }
}
