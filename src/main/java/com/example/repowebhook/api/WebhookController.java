package com.example.repowebhook.api;

import com.example.repowebhook.api.dto.CreateWebhookRequest;
import com.example.repowebhook.api.dto.DeliveryResponse;
import com.example.repowebhook.api.dto.UpdateWebhookRequest;
import com.example.repowebhook.api.dto.WebhookResponse;
import com.example.repowebhook.domain.Webhook;
import com.example.repowebhook.domain.WebhookDelivery;
import com.example.repowebhook.service.DeliveryService;
import com.example.repowebhook.service.WebhookService;
import com.example.repowebhook.service.WebhookService.WebhookSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Repository webhook API, shaped after GitHub's:
 * <pre>
 * POST   /api/repos/{owner}/{repo}/hooks                                   create
 * GET    /api/repos/{owner}/{repo}/hooks                                   list
 * GET    /api/repos/{owner}/{repo}/hooks/{id}                              get
 * PATCH  /api/repos/{owner}/{repo}/hooks/{id}                              update
 * DELETE /api/repos/{owner}/{repo}/hooks/{id}                              delete
 * POST   /api/repos/{owner}/{repo}/hooks/{id}/pings                        send a ping
 * GET    /api/repos/{owner}/{repo}/hooks/{id}/deliveries                   delivery log
 * GET    /api/repos/{owner}/{repo}/hooks/{id}/deliveries/{deliveryId}      delivery detail
 * POST   /api/repos/{owner}/{repo}/hooks/{id}/deliveries/{deliveryId}/attempts  redeliver
 * </pre>
 * NOTE: there is no authentication. Put this behind Spring Security and check that the caller has admin
 * rights on {owner}/{repo} before exposing it.
 */
@RestController
@Validated
@RequestMapping("/api/repos/{owner}/{repo}/hooks")
public class WebhookController {

    private final WebhookService webhookService;
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookService webhookService, DeliveryService deliveryService, ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<WebhookResponse> create(@PathVariable String owner, @PathVariable String repo,
                                                  @Valid @RequestBody CreateWebhookRequest request) {
        var config = request.config();
        Webhook hook = webhookService.create(owner, repo,
                new WebhookSpec(config.url(), config.contentType(), config.secret(), request.events(), request.active()));
        WebhookResponse body = WebhookResponse.from(hook, baseUrl());
        return ResponseEntity.created(URI.create(body.url())).body(body);
    }

    @GetMapping
    public List<WebhookResponse> list(@PathVariable String owner, @PathVariable String repo) {
        String base = baseUrl();
        return webhookService.list(owner, repo).stream().map(h -> WebhookResponse.from(h, base)).toList();
    }

    @GetMapping("/{id}")
    public WebhookResponse get(@PathVariable String owner, @PathVariable String repo, @PathVariable long id) {
        return WebhookResponse.from(webhookService.get(owner, repo, id), baseUrl());
    }

    @PatchMapping("/{id}")
    public WebhookResponse update(@PathVariable String owner, @PathVariable String repo, @PathVariable long id,
                                  @Valid @RequestBody UpdateWebhookRequest request) {
        var config = request.config();
        WebhookSpec spec = new WebhookSpec(
                config == null ? null : config.url(),
                config == null ? null : config.contentType(),
                config == null ? null : config.secret(),
                request.events(),
                request.active());
        return WebhookResponse.from(webhookService.update(owner, repo, id, spec), baseUrl());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String owner, @PathVariable String repo, @PathVariable long id) {
        webhookService.delete(owner, repo, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pings")
    public ResponseEntity<Void> ping(@PathVariable String owner, @PathVariable String repo, @PathVariable long id) {
        webhookService.ping(owner, repo, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/deliveries")
    public List<DeliveryResponse> deliveries(@PathVariable String owner, @PathVariable String repo, @PathVariable long id,
                                             @RequestParam(defaultValue = "0") @Min(0) int page,
                                             @RequestParam(name = "per_page", defaultValue = "30") @Min(1) @Max(100) int perPage) {
        Webhook hook = webhookService.get(owner, repo, id);
        return deliveryService.list(hook, page, perPage).stream().map(DeliveryResponse::summary).toList();
    }

    @GetMapping("/{id}/deliveries/{deliveryId}")
    public DeliveryResponse delivery(@PathVariable String owner, @PathVariable String repo, @PathVariable long id,
                                     @PathVariable long deliveryId) {
        Webhook hook = webhookService.get(owner, repo, id);
        return DeliveryResponse.detail(deliveryService.get(hook, deliveryId), objectMapper);
    }

    @PostMapping("/{id}/deliveries/{deliveryId}/attempts")
    public ResponseEntity<DeliveryResponse> redeliver(@PathVariable String owner, @PathVariable String repo,
                                                      @PathVariable long id, @PathVariable long deliveryId) {
        Webhook hook = webhookService.get(owner, repo, id);
        WebhookDelivery redelivery = deliveryService.redeliver(hook, deliveryId);
        return ResponseEntity.accepted().body(DeliveryResponse.summary(redelivery));
    }

    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
