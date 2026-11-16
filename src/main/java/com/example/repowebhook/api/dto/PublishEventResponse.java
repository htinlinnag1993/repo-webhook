package com.example.repowebhook.api.dto;

import com.example.repowebhook.domain.EventType;
import java.util.List;

public record PublishEventResponse(EventType event, List<QueuedDelivery> deliveries) {

    public record QueuedDelivery(Long hookId, Long deliveryId, String guid) {
    }
}
