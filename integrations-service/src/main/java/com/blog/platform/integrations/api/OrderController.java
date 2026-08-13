package com.blog.platform.integrations.api;

import com.blog.platform.common.api.ApiResponse;
import com.blog.platform.integrations.api.dto.OrderDtos.CreateOrderRequest;
import com.blog.platform.integrations.api.dto.OrderDtos.CreateOrderResponse;
import com.blog.platform.integrations.config.OrderProperties;
import com.blog.platform.integrations.service.OrderOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/public/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderOrchestrator orderOrchestrator;
    private final OrderProperties orderProperties;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> create(
            @Valid @RequestBody CreateOrderRequest request,
            HttpServletRequest httpRequest
    ) {
        assertOrderSecret(httpRequest);
        CreateOrderResponse result = orderOrchestrator.process(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(result));
    }

    private void assertOrderSecret(HttpServletRequest request) {
        if (!orderProperties.secretConfigured()) {
            return;
        }
        String provided = request.getHeader("X-Order-Secret");
        if (provided == null || provided.isBlank()) {
            provided = request.getParameter("secret");
        }
        if (provided == null || !orderProperties.secret().equals(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid order secret");
        }
    }
}
