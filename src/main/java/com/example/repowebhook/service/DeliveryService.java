package com.example.repowebhook.service;

import com.example.repowebhook.config.DeliveryProperties;
import com.example.repowebhook.delivery.DeliveryEnqueuedEvent;
import com.example.repowebhook.domain.EventType;
import com.example.repowebhook.domain.Webhook;
import com.example.repowebhook.domain.WebhookDelivery;
import com.example.repowebhook.repository.WebhookDeliveryRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryService {

    private final WebhookDeliveryRepository deliveries;
    private final ApplicationEventPublisher events;
    private final DeliveryProperties properties;

    public DeliveryService(WebhookDeliveryRepository deliveries, ApplicationEventPublisher events,
                           DeliveryProperties properties) {
        this.deliveries = deliveries;
        this.events = events;
        this.properties = properties;
    }

    /**
     * Persists a delivery and schedules its first attempt. The HTTP call happens only after the caller's
     * transaction commits, so a rolled-back event is never sent.
     */
    @Transactional
    public WebhookDelivery enqueue(Webhook hook, EventType event, String action, String payloadJson, boolean redelivery) {
        WebhookDelivery delivery = new WebhookDelivery(hook.getId(), event, action, payloadJson, redelivery);
        // Safety net: if the immediate attempt is lost, the scheduler picks this row up after the grace period.
        delivery.setNextAttemptAt(Instant.now().plus(properties.pendingGrace()));
        deliveries.save(delivery);
        events.publishEvent(new DeliveryEnqueuedEvent(delivery.getId()));
        return delivery;
    }

    @Transactional(readOnly = true)
    public List<WebhookDelivery> list(Webhook hook, int page, int perPage) {
        return deliveries.findByWebhookIdOrderByIdDesc(hook.getId(), PageRequest.of(page, perPage)).getContent();
    }

    @Transactional(readOnly = true)
    public WebhookDelivery get(Webhook hook, long deliveryId) {
        return deliveries.findByIdAndWebhookId(deliveryId, hook.getId())
                .orElseThrow(() -> new NotFoundException("Delivery " + deliveryId + " not found"));
    }

    /** Re-sends the original payload as a new delivery (new GUID), like GitHub's "Redeliver" button. */
    @Transactional
    public WebhookDelivery redeliver(Webhook hook, long deliveryId) {
        WebhookDelivery original = get(hook, deliveryId);
        return enqueue(hook, original.getEvent(), original.getAction(), original.getPayload(), true);
    }
}
