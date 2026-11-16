package com.example.repowebhook.api.dto;

import com.example.repowebhook.domain.ContentType;
import com.example.repowebhook.domain.EventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * <pre>
 * {
 *   "active": true,
 *   "events": ["push", "pull_request"],
 *   "config": { "url": "https://example.com/hook", "content_type": "json", "secret": "s3cr3t" }
 * }
 * </pre>
 * {@code events} defaults to {@code ["push"]}; {@code active} defaults to {@code true}.
 */
public record CreateWebhookRequest(
        Boolean active,
        Set<EventType> events,
        @NotNull @Valid Config config) {

    public record Config(
            @NotBlank @Size(max = 2048) String url,
            ContentType contentType,
            @Size(max = 256) String secret) {
    }
}
