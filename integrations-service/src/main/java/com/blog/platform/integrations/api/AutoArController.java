package com.blog.platform.integrations.api;

import com.blog.platform.integrations.api.dto.AmoDtos.AutoArResult;
import com.blog.platform.integrations.api.dto.AmoDtos.WidgetRequest;
import com.blog.platform.integrations.service.AutoArService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * amoCRM Salesbot widget_request handler: last 4 phone digits → contact AR field.
 * Must answer 200 within ~2s; bot continues via return_url.
 */
@RestController
@RequestMapping("/amocrm")
@RequiredArgsConstructor
public class AutoArController {

    private final AutoArService autoArService;

    @PostMapping("/autoar")
    public ResponseEntity<Map<String, Object>> autoar(@RequestBody WidgetRequest request) {
        AutoArResult result = autoArService.process(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("ar", result.ar());
        body.put("status", result.status());
        return ResponseEntity.ok(body);
    }
}
