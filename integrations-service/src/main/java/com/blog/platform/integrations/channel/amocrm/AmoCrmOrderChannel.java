package com.blog.platform.integrations.channel.amocrm;

import com.blog.platform.integrations.api.dto.OrderDtos.OrderMeta;
import com.blog.platform.integrations.api.dto.OrderDtos.OrderUtm;
import com.blog.platform.integrations.config.AmoCrmProperties;
import com.blog.platform.integrations.config.OrderProperties;
import com.blog.platform.integrations.domain.OrderContext;
import com.blog.platform.integrations.service.AmoCrmApiClient;
import com.blog.platform.integrations.service.AutoArService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * Primary channel: creates amoCRM contact + lead + note. Must succeed for order acceptance.
 */
@Component
public class AmoCrmOrderChannel {

    private static final Logger log = LoggerFactory.getLogger(AmoCrmOrderChannel.class);

    private final OrderProperties orderProperties;
    private final AmoCrmProperties amoCrmProperties;
    private final AmoCrmApiClient amoCrmApiClient;

    public AmoCrmOrderChannel(
            OrderProperties orderProperties,
            AmoCrmProperties amoCrmProperties,
            AmoCrmApiClient amoCrmApiClient
    ) {
        this.orderProperties = orderProperties;
        this.amoCrmProperties = amoCrmProperties;
        this.amoCrmApiClient = amoCrmApiClient;
    }

    public boolean configured() {
        return amoCrmProperties.apiConfigured();
    }

    public OrderContext push(OrderContext context) {
        long contactId = amoCrmApiClient.findOrCreateContact(
                        context.request().name().trim(),
                        context.phoneDigits()
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "crm contact failed"));

        if (!context.ar().isBlank()) {
            amoCrmApiClient.patchContactArField(contactId, amoCrmProperties.arFieldId(), context.ar());
        }

        long leadId = amoCrmApiClient.createLead(
                        context.leadName(),
                        orderProperties.usePipeline() ? orderProperties.pipelineId() : null,
                        orderProperties.usePipeline() ? orderProperties.statusId() : null,
                        buildLeadCustomFields(context.request().meta()),
                        buildTags()
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "crm lead failed"));

        if (!amoCrmApiClient.linkContactToLead(leadId, contactId)) {
            log.warn("orders: lead {} created but contact {} link failed", leadId, contactId);
        }
        if (!amoCrmApiClient.addLeadNote(leadId, context.note())) {
            log.warn("orders: lead {} note failed", leadId);
        }

        return context.withCrm(leadId, contactId);
    }

    private List<String> buildTags() {
        List<String> tags = new ArrayList<>();
        if (orderProperties.tagSite() != null && !orderProperties.tagSite().isBlank()) {
            tags.add(orderProperties.tagSite().trim());
        }
        if (orderProperties.tagParts() != null && !orderProperties.tagParts().isBlank()) {
            tags.add(orderProperties.tagParts().trim());
        }
        return tags;
    }

    private List<AmoCrmApiClient.CustomFieldValue> buildLeadCustomFields(OrderMeta meta) {
        List<AmoCrmApiClient.CustomFieldValue> fields = new ArrayList<>();
        if (meta == null || meta.utm() == null) {
            return fields;
        }
        OrderUtm utm = meta.utm();
        if (utm.campaign() != null && !utm.campaign().isBlank()) {
            fields.add(new AmoCrmApiClient.CustomFieldValue(
                    orderProperties.utmCampaignFieldId(),
                    utm.campaign().trim()
            ));
        }
        if (utm.referrer() != null && !utm.referrer().isBlank()) {
            fields.add(new AmoCrmApiClient.CustomFieldValue(
                    orderProperties.utmReferrerFieldId(),
                    utm.referrer().trim()
            ));
        }
        return fields;
    }
}
