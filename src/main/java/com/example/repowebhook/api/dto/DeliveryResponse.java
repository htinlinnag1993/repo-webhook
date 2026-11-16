package com.example.repowebhook.api.dto;

import com.example.repowebhook.domain.DeliveryStatus;
import com.example.repowebhook.domain.EventType;
import com.example.repowebhook.domain.WebhookDelivery;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;

/** Delivery log entry. {@code request}/{@code response} are only included on the single-delivery endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeliveryResponse(
        Long id,
        String guid,
        Long hookId,
        EventType event,
        String action,
        DeliveryStatus status,
        Integer statusCode,
        boolean redelivery,
        int attempts,
        Long durationMs,
        String error,
        Instant createdAt,
        Instant deliveredAt,
        Instant nextAttemptAt,
        Request request,
        Response response) {

    public record Request(JsonNode headers, JsonNode payload) {
    }

    public record Response(String body) {
    }

    public static DeliveryResponse summary(WebhookDelivery d) {
        return of(d, null, null);
    }

    public static DeliveryResponse detail(WebhookDelivery d, ObjectMapper mapper) {
        return of(d,
                new Request(parse(mapper, d.getRequestHeaders()), parse(mapper, d.getPayload())),
                new Response(d.getResponseBody()));
    }

    private static DeliveryResponse of(WebhookDelivery d, Request request, Response response) {
        return new DeliveryResponse(d.getId(), d.getGuid(), d.getWebhookId(), d.getEvent(), d.getAction(),
                d.getStatus(), d.getResponseStatus(), d.isRedelivery(), d.getAttempts(), d.getDurationMs(),
                d.getErrorMessage(), d.getCreatedAt(), d.getLastAttemptAt(), d.getNextAttemptAt(), request, response);
    }

    private static JsonNode parse(ObjectMapper mapper, String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            return mapper.getNodeFactory().textNode(json);
        }
    }
}
