package com.example.repowebhook.api.dto;

import com.example.repowebhook.domain.EventType;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Simulates an event on a repository.
 * <pre>
 * { "event": "pull_request", "action": "opened", "payload": { "number": 42, "pull_request": { ... } } }
 * </pre>
 * {@code payload} fields are merged into the top level of the delivered body, alongside {@code repository}.
 */
public record PublishEventRequest(
        @NotNull EventType event,
        String action,
        Map<String, Object> payload) {
}
