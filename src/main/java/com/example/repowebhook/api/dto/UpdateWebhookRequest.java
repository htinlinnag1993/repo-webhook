package com.example.repowebhook.api.dto;

import com.example.repowebhook.domain.ContentType;
import com.example.repowebhook.domain.EventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** All fields optional; omitted fields are left unchanged. Send {@code "secret": ""} to remove the secret. */
public record UpdateWebhookRequest(
        Boolean active,
        Set<EventType> events,
        @Valid Config config) {

    public record Config(
            @Size(min = 1, max = 2048) String url,
            ContentType contentType,
            @Size(max = 256) String secret) {
    }
}
