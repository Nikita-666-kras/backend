package com.blog.platform.integrations.channel.max;

import com.blog.platform.integrations.channel.OrderNotifier;
import com.blog.platform.integrations.config.MaxProperties;
import com.blog.platform.integrations.domain.OrderContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class MaxOrderNotifier implements OrderNotifier {

    private static final Logger log = LoggerFactory.getLogger(MaxOrderNotifier.class);
    private static final String API = "https://platform-api2.max.ru/messages";
    private static final int TEXT_LIMIT = 3900;

    private final MaxProperties properties;
    private final RestClient restClient;

    public MaxOrderNotifier(MaxProperties properties, @Qualifier("maxRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @PostConstruct
    void logStatus() {
        if (enabled()) {
            log.info("max: enabled recipients={}", properties.userIds());
        } else {
            log.info("max: disabled (MAX_BOT_TOKEN or MAX_ORDERS_USER_IDS missing)");
        }
    }

    @Override
    public int order() {
        return 15;
    }

    @Override
    public boolean enabled() {
        return properties.configured();
    }

    @Override
    public void notify(OrderContext context) {
        if (!enabled()) {
            return;
        }
        String text = buildText(context);
        for (Long userId : properties.userIds()) {
            sendToUser(userId, text, context.orderId());
        }
    }

    private void sendToUser(long userId, String text, String orderId) {
        try {
            restClient.post()
                    .uri(API + "?user_id=" + userId)
                    .header("Authorization", properties.botToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "text", text,
                            "format", "html",
                            "notify", true
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("max: order {} sent to user_id={}", orderId, userId);
        } catch (RestClientResponseException ex) {
            log.error("max: notify failed orderId={} user_id={} status={} body={}",
                    orderId, userId, ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("max: notify failed orderId={} user_id={}: {}", orderId, userId, ex.getMessage());
        }
    }

    private static String buildText(OrderContext context) {
        String raw = "Новый заказ " + context.orderId()
                + "\n" + escape(context.request().name()) + " · "
                + escape(context.request().phone())
                + "\n\n" + escape(context.note());
        if (raw.length() <= TEXT_LIMIT) {
            return raw;
        }
        return raw.substring(0, TEXT_LIMIT) + "…";
    }

    private static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
