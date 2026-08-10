package com.blog.platform.integrations.api;

import com.blog.platform.integrations.service.PipelineAutoArService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Digital Pipeline «API: отправить webhook» — без Salesbot widget_request.
 */
@RestController
@RequestMapping("/amocrm")
@RequiredArgsConstructor
public class PipelineAutoArController {

    private final PipelineAutoArService pipelineAutoArService;

    @PostMapping("/pipeline-autoar")
    public ResponseEntity<Map<String, Object>> pipelineAutoar(
            @RequestBody(required = false) String body,
            HttpServletRequest request
    ) {
        String contentType = request.getContentType();
        pipelineAutoArService.handleWebhook(body == null ? "" : body, contentType);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
