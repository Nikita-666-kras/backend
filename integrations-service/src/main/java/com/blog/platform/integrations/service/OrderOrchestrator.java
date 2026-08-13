package com.blog.platform.integrations.service;

import com.blog.platform.integrations.api.dto.OrderDtos.CreateOrderRequest;
import com.blog.platform.integrations.api.dto.OrderDtos.CreateOrderResponse;
import com.blog.platform.integrations.channel.OrderNotifier;
import com.blog.platform.integrations.channel.amocrm.AmoCrmOrderChannel;
import com.blog.platform.integrations.config.OrderProperties;
import com.blog.platform.integrations.domain.OrderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Accepts public orders and orchestrates outbound integrations (CRM required, others best-effort).
 */
@Service
public class OrderOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderOrchestrator.class);
    private static final Pattern PHONE_DIGITS = Pattern.compile("\\D");
    private static final DateTimeFormatter ORDER_ID_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderProperties orderProperties;
    private final AmoCrmOrderChannel amoCrmOrderChannel;
    private final List<OrderNotifier> notifiers;

    public OrderOrchestrator(
            OrderProperties orderProperties,
            AmoCrmOrderChannel amoCrmOrderChannel,
            List<OrderNotifier> notifiers
    ) {
        this.orderProperties = orderProperties;
        this.amoCrmOrderChannel = amoCrmOrderChannel;
        this.notifiers = notifiers.stream()
                .sorted(Comparator.comparingInt(OrderNotifier::order))
                .toList();
    }

    public CreateOrderResponse process(CreateOrderRequest request) {
        if (!orderProperties.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "orders disabled");
        }
        if (!amoCrmOrderChannel.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "crm not configured");
        }
        if (!Boolean.TRUE.equals(request.consentPd())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "consentPd required");
        }

        String phone = normalizePhone(request.phone());
        if (phone.length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid phone");
        }

        String orderId = buildOrderId();
        String ar = AutoArService.extractAr(phone);
        OrderContext context = new OrderContext(
                orderId,
                request,
                phone,
                ar,
                OrderTextFormatter.leadName(request, ar),
                OrderTextFormatter.orderNote(request, orderId),
                null,
                null
        );

        OrderContext afterCrm = amoCrmOrderChannel.push(context);
        dispatchNotifiers(afterCrm);

        log.info("orders: orderId={} leadId={} contactId={} items={} phone={}",
                orderId,
                afterCrm.leadId(),
                afterCrm.contactId(),
                request.items().size(),
                maskPhone(phone));

        return new CreateOrderResponse(
                orderId,
                afterCrm.leadId(),
                afterCrm.contactId(),
                "accepted"
        );
    }

    private void dispatchNotifiers(OrderContext context) {
        for (OrderNotifier notifier : notifiers) {
            if (!notifier.enabled()) {
                continue;
            }
            try {
                notifier.notify(context);
            } catch (Exception ex) {
                log.error("orders: notifier {} failed orderId={}: {}",
                        notifier.getClass().getSimpleName(), context.orderId(), ex.getMessage());
            }
        }
    }

    static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        return PHONE_DIGITS.matcher(raw.trim()).replaceAll("");
    }

    private static String buildOrderId() {
        return "ord_" + LocalDateTime.now().format(ORDER_ID_TS) + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String maskPhone(String digits) {
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.max(0, digits.length() - 4)) + digits.substring(digits.length() - 4);
    }
}
