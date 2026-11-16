package com.example.repowebhook.api.dto;

import com.example.repowebhook.domain.ContentType;
import com.example.repowebhook.domain.EventType;
import com.example.repowebhook.domain.Webhook;
import java.time.Instant;
import java.util.List;

public record WebhookResponse(
        Long id,
        String type,
        boolean active,
        List<EventType> events,
        ConfigView config,
        Instant createdAt,
        Instant updatedAt,
        String url,
        String pingUrl,
        String deliveriesUrl) {

    /** The secret is never echoed back; only whether one is set. */
    public record ConfigView(String url, ContentType contentType, String secret) {
    }

    public static WebhookResponse from(Webhook hook, String baseUrl) {
        String self = baseUrl + "/api/repos/" + hook.getOwner() + "/" + hook.getRepo() + "/hooks/" + hook.getId();
        return new WebhookResponse(
                hook.getId(),
                "Repository",
                hook.isActive(),
                hook.getEvents().stream().sorted().toList(),
                new ConfigView(hook.getUrl(), hook.getContentType(), hook.hasSecret() ? "********" : null),
                hook.getCreatedAt(),
                hook.getUpdatedAt(),
                self,
                self + "/pings",
                self + "/deliveries");
    }
}
