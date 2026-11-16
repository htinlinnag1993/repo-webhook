package com.example.repowebhook.service;

import com.example.repowebhook.domain.ContentType;
import com.example.repowebhook.domain.EventType;
import com.example.repowebhook.domain.Webhook;
import com.example.repowebhook.repository.WebhookDeliveryRepository;
import com.example.repowebhook.repository.WebhookRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and management of webhooks on a repository. */
@Service
@Transactional
public class WebhookService {

    private static final List<String> ZEN = List.of(
            "Small payloads travel fast.",
            "Retry with patience, deliver with certainty.",
            "Every event deserves a receipt.",
            "Verify the signature, trust the payload.");

    private final WebhookRepository webhooks;
    private final WebhookDeliveryRepository deliveryRows;
    private final DeliveryService deliveries;
    private final WebhookUrlValidator urlValidator;
    private final Json json;

    public WebhookService(WebhookRepository webhooks, WebhookDeliveryRepository deliveryRows, DeliveryService deliveries,
                          WebhookUrlValidator urlValidator, Json json) {
        this.webhooks = webhooks;
        this.deliveryRows = deliveryRows;
        this.deliveries = deliveries;
        this.urlValidator = urlValidator;
        this.json = json;
    }

    public record WebhookSpec(String url, ContentType contentType, String secret, Set<EventType> events, Boolean active) {
    }

    /** Creates a webhook and, like GitHub, immediately sends it a {@code ping} event. */
    public Webhook create(String owner, String repo, WebhookSpec spec) {
        urlValidator.validate(spec.url());
        Set<EventType> events = spec.events() == null || spec.events().isEmpty() ? Set.of(EventType.PUSH) : spec.events();
        validateEvents(events);

        Webhook hook = new Webhook();
        hook.setOwner(owner);
        hook.setRepo(repo);
        hook.setUrl(spec.url());
        hook.setContentType(spec.contentType() == null ? ContentType.JSON : spec.contentType());
        hook.setSecret(blankToNull(spec.secret()));
        hook.setEvents(events);
        hook.setActive(spec.active() == null || spec.active());
        webhooks.save(hook);

        sendPing(hook);
        return hook;
    }

    @Transactional(readOnly = true)
    public List<Webhook> list(String owner, String repo) {
        return webhooks.findByOwnerAndRepoOrderByIdAsc(owner, repo);
    }

    @Transactional(readOnly = true)
    public Webhook get(String owner, String repo, long id) {
        return webhooks.findByIdAndOwnerAndRepo(id, owner, repo)
                .orElseThrow(() -> new NotFoundException("Webhook " + id + " not found on " + owner + "/" + repo));
    }

    /** Partial update: null fields are left unchanged. An empty-string secret removes the secret. */
    public Webhook update(String owner, String repo, long id, WebhookSpec spec) {
        Webhook hook = get(owner, repo, id);
        if (spec.url() != null) {
            urlValidator.validate(spec.url());
            hook.setUrl(spec.url());
        }
        if (spec.contentType() != null) {
            hook.setContentType(spec.contentType());
        }
        if (spec.secret() != null) {
            hook.setSecret(blankToNull(spec.secret()));
        }
        if (spec.events() != null) {
            if (spec.events().isEmpty()) {
                throw new InvalidWebhookException("events must not be empty");
            }
            validateEvents(spec.events());
            hook.setEvents(spec.events());
        }
        if (spec.active() != null) {
            hook.setActive(spec.active());
        }
        return hook;
    }

    public void delete(String owner, String repo, long id) {
        Webhook hook = get(owner, repo, id);
        deliveryRows.deleteByWebhookId(hook.getId());
        webhooks.delete(hook);
    }

    public void ping(String owner, String repo, long id) {
        sendPing(get(owner, repo, id));
    }

    private void sendPing(Webhook hook) {
        Map<String, Object> hookView = new LinkedHashMap<>();
        hookView.put("id", hook.getId());
        hookView.put("type", "Repository");
        hookView.put("active", hook.isActive());
        hookView.put("events", hook.getEvents().stream().map(EventType::value).sorted().toList());
        hookView.put("config", Map.of("url", hook.getUrl(), "content_type", hook.getContentType().value()));
        hookView.put("created_at", String.valueOf(hook.getCreatedAt()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zen", ZEN.get(ThreadLocalRandom.current().nextInt(ZEN.size())));
        payload.put("hook_id", hook.getId());
        payload.put("hook", hookView);
        payload.put("repository", EventService.repositoryView(hook.getOwner(), hook.getRepo()));

        deliveries.enqueue(hook, EventType.PING, null, json.write(payload), false);
    }

    private static void validateEvents(Set<EventType> events) {
        if (events.contains(EventType.PING)) {
            throw new InvalidWebhookException("'ping' is sent automatically and cannot be subscribed to");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
