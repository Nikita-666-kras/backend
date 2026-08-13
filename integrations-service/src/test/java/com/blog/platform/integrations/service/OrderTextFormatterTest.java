package com.blog.platform.integrations.service;

import com.blog.platform.integrations.api.dto.OrderDtos.CreateOrderRequest;
import com.blog.platform.integrations.api.dto.OrderDtos.OrderItem;
import com.blog.platform.integrations.api.dto.OrderDtos.OrderMeta;
import com.blog.platform.integrations.api.dto.OrderDtos.OrderUtm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderTextFormatterTest {

    @Test
    void leadName_includesArAndCount() {
        CreateOrderRequest req = sampleRequest();
        assertEquals("Запчасти · 2 поз. · Ар 4567 · Иван", OrderTextFormatter.leadName(req, "4567"));
    }

    @Test
    void orderNote_listsItemsAndTotal() {
        String note = OrderTextFormatter.orderNote(sampleRequest(), "ord_test_1");
        assertTrue(note.contains("ord_test_1"));
        assertTrue(note.contains("DJI-123"));
        assertTrue(note.contains("Ориентир по ценам:"));
        assertTrue(note.contains("3") && note.contains("000"));
        assertTrue(note.contains("Позиций «По запросу»: 1"));
    }

    @Test
    void normalizePhone_stripsNonDigits() {
        assertEquals("79001234567", OrderOrchestrator.normalizePhone("+7 (900) 123-45-67"));
    }

    private static CreateOrderRequest sampleRequest() {
        return new CreateOrderRequest(
                "Иван",
                "+79001234567",
                null,
                List.of(
                        new OrderItem("DJI-123", null, "Пропеллер", 2, 1500.0),
                        new OrderItem("HD580-1", null, "Мотор", 1, null)
                ),
                new OrderMeta("parts", "https://atris.su/parts", new OrderUtm("camp", "https://atris.su/parts"), null),
                true,
                false
        );
    }
}
