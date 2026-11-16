package com.example.repowebhook.service;

import com.example.repowebhook.domain.EventType;
import com.example.repowebhook.domain.Webhook;
import com.example.repowebhook.domain.WebhookDelivery;
import com.example.repowebhook.repository.WebhookRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entry point for "something happened on a repository". In a real system this would be called from your
 * domain code (a push was received, a PR was opened...) or from a message-queue consumer; here it is
 * also exposed over HTTP so you can simulate events.
 */
@Service
public class EventService {

    private final WebhookRepository webhooks;
    private final DeliveryService deliveries;
    private final Json json;

    public EventService(WebhookRepository webhooks, DeliveryService deliveries, Json json) {
        this.webhooks = webhooks;
        this.deliveries = deliveries;
        this.json = json;
    }

    /** Fans the event out to every active webhook on the repo that subscribes to it. */
    @Transactional
    public List<WebhookDelivery> publish(String owner, String repo, EventType event, String action,
                                         Map<String, Object> details) {
        if (event == EventType.WILDCARD || event == EventType.PING) {
            throw new InvalidWebhookException("'" + event.value() + "' cannot be published as an event");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        if (action != null && !action.isBlank()) {
            payload.put("action", action);
        }
        if (details != null) {
            payload.putAll(details);
        }
        payload.put("repository", repositoryView(owner, repo));
        String payloadJson = json.write(payload);

        return webhooks.findByOwnerAndRepoAndActiveTrue(owner, repo).stream()
                .filter(hook -> hook.subscribesTo(event))
                .map((Webhook hook) -> deliveries.enqueue(hook, event, action, payloadJson, false))
                .toList();
    }

    static Map<String, Object> repositoryView(String owner, String repo) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", repo);
        view.put("full_name", owner + "/" + repo);
        view.put("owner", Map.of("login", owner));
        return view;
    }
}
