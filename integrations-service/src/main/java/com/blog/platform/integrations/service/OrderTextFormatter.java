package com.blog.platform.integrations.service;

import com.blog.platform.integrations.api.dto.OrderDtos.CreateOrderRequest;
import com.blog.platform.integrations.api.dto.OrderDtos.OrderItem;
import com.blog.platform.integrations.api.dto.OrderDtos.OrderMeta;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

final class OrderTextFormatter {

    private static final NumberFormat RUB = NumberFormat.getInstance(new Locale("ru", "RU"));

    private OrderTextFormatter() {
    }

    static String leadName(CreateOrderRequest request, String ar) {
        int n = request.items().size();
        String name = request.name().trim();
        if (ar != null && !ar.isBlank()) {
            return "Запчасти · " + n + " поз. · Ар " + ar + " · " + name;
        }
        return "Запчасти · " + n + " поз. · " + name;
    }

    static String orderNote(CreateOrderRequest request, String orderId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Заказ ").append(orderId).append("\n");
        sb.append("Источник: ").append(sourceLabel(request.meta())).append("\n");
        if (request.meta() != null && request.meta().pageUrl() != null && !request.meta().pageUrl().isBlank()) {
            sb.append("Страница: ").append(request.meta().pageUrl().trim()).append("\n");
        }
        sb.append("\nСостав заказа (").append(request.items().size()).append(" поз.):\n");
        Totals totals = appendLines(sb, request.items());
        if (totals.sum > 0) {
            sb.append("\nОриентир по ценам: ").append(RUB.format(totals.sum)).append(" ₽");
        }
        if (totals.request > 0) {
            sb.append("\nПозиций «По запросу»: ").append(totals.request);
        }
        if (request.email() != null && !request.email().isBlank()) {
            sb.append("\nEmail: ").append(request.email().trim());
        }
        return sb.toString();
    }

    private static String sourceLabel(OrderMeta meta) {
        if (meta == null || meta.source() == null || meta.source().isBlank()) {
            return "АТРИС — сайт";
        }
        return meta.source().trim();
    }

    private static Totals appendLines(StringBuilder sb, List<OrderItem> items) {
        Totals totals = new Totals();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            int q = item.normalizedQty();
            sb.append(i + 1)
                    .append(". ")
                    .append(item.sku())
                    .append(" — ")
                    .append(item.title())
                    .append(" ×")
                    .append(q)
                    .append(" — ")
                    .append(formatPrice(item.price()))
                    .append('\n');
            if (item.price() != null && item.price() > 0) {
                totals.sum += item.price() * q;
                totals.priced += q;
            } else {
                totals.request += q;
            }
        }
        return totals;
    }

    static String formatPrice(Double price) {
        if (price == null || price <= 0) {
            return "По запросу";
        }
        return RUB.format(price) + " ₽";
    }

    private static final class Totals {
        double sum;
        int priced;
        int request;
    }
}
