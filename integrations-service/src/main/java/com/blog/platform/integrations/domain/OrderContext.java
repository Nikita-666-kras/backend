package com.blog.platform.integrations.domain;

import com.blog.platform.integrations.api.dto.OrderDtos.CreateOrderRequest;

/**
 * Normalized order payload passed through integration channels (CRM, Telegram, email, …).
 */
public record OrderContext(
        String orderId,
        CreateOrderRequest request,
        String phoneDigits,
        String ar,
        String leadName,
        String note,
        Long leadId,
        Long contactId
) {
    public OrderContext withCrm(long leadId, long contactId) {
        return new OrderContext(orderId, request, phoneDigits, ar, leadName, note, leadId, contactId);
    }
}
