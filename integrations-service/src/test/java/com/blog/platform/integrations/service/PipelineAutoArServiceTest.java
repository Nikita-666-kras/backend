package com.blog.platform.integrations.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineAutoArServiceTest {

    private final PipelineAutoArService service = new PipelineAutoArService(
            null,
            null,
            new com.fasterxml.jackson.databind.ObjectMapper()
    );

    @Test
    void parseLeadIds_fromDpJson() {
        String json = """
                {
                  "leads": {
                    "status": [
                      {
                        "id": 4831596,
                        "status_id": 16203337
                      }
                    ]
                  }
                }
                """;
        List<Long> ids = service.parseLeadIds(json, "application/json");
        assertEquals(List.of(4831596L), ids);
    }

    @Test
    void parseLeadIds_fromFormBody() {
        String form = "leads%5Bstatus%5D%5B0%5D%5Bid%5D=999888&account%5Bid%5D=1";
        List<Long> ids = service.parseLeadIds(form, "application/x-www-form-urlencoded");
        assertEquals(List.of(999888L), ids);
    }

    @Test
    void parseLeadIds_emptyWhenNoLead() {
        assertTrue(service.parseLeadIds("{}", "application/json").isEmpty());
    }
}
