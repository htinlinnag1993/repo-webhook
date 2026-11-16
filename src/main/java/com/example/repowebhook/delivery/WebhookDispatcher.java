package com.example.repowebhook.delivery;

import com.example.repowebhook.config.DeliveryProperties;
import com.example.repowebhook.domain.ContentType;
import com.example.repowebhook.domain.DeliveryStatus;
import com.example.repowebhook.domain.EventType;
import com.example.repowebhook.domain.Webhook;
import com.example.repowebhook.domain.WebhookDelivery;
import com.example.repowebhook.repository.WebhookDeliveryRepository;
import com.example.repowebhook.repository.WebhookRepository;
import com.example.repowebhook.service.InvalidWebhookException;
import com.example.repowebhook.service.WebhookUrlValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Performs a single delivery attempt: claim the row, POST to the subscriber, record the outcome. */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final EnumSet<DeliveryStatus> CLAIMABLE = EnumSet.of(DeliveryStatus.PENDING, DeliveryStatus.RETRYING);

    private final WebhookDeliveryRepository deliveries;
    private final WebhookRepository webhooks;
    private final RestClient restClient;
    private final DeliveryProperties properties;
    private final WebhookUrlValidator urlValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public WebhookDispatcher(WebhookDeliveryRepository deliveries, WebhookRepository webhooks, RestClient webhookRestClient,
                             DeliveryProperties properties, WebhookUrlValidator urlValidator, ObjectMapper objectMapper,
                             TransactionTemplate tx) {
        this.deliveries = deliveries;
        this.webhooks = webhooks;
        this.restClient = webhookRestClient;
        this.properties = properties;
        this.urlValidator = urlValidator;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    public void attempt(long deliveryId) {
        Integer claimed = tx.execute(s -> deliveries.transition(deliveryId, CLAIMABLE, DeliveryStatus.IN_PROGRESS));
        if (claimed == null || claimed == 0) {
            log.debug("Delivery {} already claimed or finished; skipping", deliveryId);
            return;
        }

        Optional<WebhookDelivery> maybeDelivery = deliveries.findById(deliveryId);
        if (maybeDelivery.isEmpty()) {
            return; // webhook (and its deliveries) deleted in the meantime
        }
        WebhookDelivery delivery = maybeDelivery.get();

        // Manual pings and redeliveries are allowed on inactive hooks (useful for testing an endpoint
        // before turning it on); regular events queued before deactivation are dropped.
        Outcome outcome = webhooks.findById(delivery.getWebhookId())
                .map(hook -> hook.isActive() || delivery.isRedelivery() || delivery.getEvent() == EventType.PING
                        ? send(hook, delivery)
                        : Outcome.failure(Map.of(), "Webhook is inactive", false, 0))
                .orElseGet(() -> Outcome.failure(Map.of(), "Webhook no longer exists", false, 0));

        tx.executeWithoutResult(s -> record(deliveryId, outcome));
    }

    private Outcome send(Webhook hook, WebhookDelivery delivery) {
        try {
            urlValidator.validate(hook.getUrl());
        } catch (InvalidWebhookException e) {
            return Outcome.failure(Map.of(), e.getMessage(), false, 0);
        }

        byte[] body = encodeBody(hook.getContentType(), delivery.getPayload());

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, hook.getContentType().mediaType());
        headers.put(HttpHeaders.USER_AGENT, properties.userAgent());
        headers.put(WebhookHeaders.EVENT, delivery.getEvent().value());
        headers.put(WebhookHeaders.DELIVERY, delivery.getGuid());
        headers.put(WebhookHeaders.HOOK_ID, String.valueOf(hook.getId()));
        if (hook.hasSecret()) {
            headers.put(WebhookHeaders.SIGNATURE_256, PayloadSigner.sign(hook.getSecret(), body));
        }

        long start = System.nanoTime();
        try {
            Outcome outcome = restClient.post()
                    .uri(URI.create(hook.getUrl()))
                    .headers(h -> headers.forEach(h::set))
                    .body(body)
                    // exchange() bypasses default status handlers, so 4xx/5xx come back as data, not exceptions
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        String responseBody = readCapped(response.getBody());
                        return Outcome.response(headers, status, responseBody, elapsedMs(start));
                    });
            log.info("Delivery {} ({}) to hook {} -> HTTP {}", delivery.getGuid(), delivery.getEvent().value(),
                    hook.getId(), outcome.statusCode());
            return outcome;
        } catch (RestClientException e) {
            log.info("Delivery {} to hook {} failed: {}", delivery.getGuid(), hook.getId(), e.getMessage());
            return Outcome.failure(headers, e.getMostSpecificCause().getMessage(), true, elapsedMs(start));
        }
    }

    private void record(long deliveryId, Outcome outcome) {
        WebhookDelivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            return;
        }
        Instant now = Instant.now();
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLastAttemptAt(now);
        delivery.setRequestHeaders(toJson(outcome.requestHeaders()));
        delivery.setResponseStatus(outcome.statusCode());
        delivery.setResponseBody(outcome.responseBody());
        delivery.setErrorMessage(truncate(outcome.error(), 1024));
        delivery.setDurationMs(outcome.durationMs());

        if (outcome.succeeded()) {
            delivery.setStatus(DeliveryStatus.SUCCEEDED);
            delivery.setNextAttemptAt(null);
            delivery.setCompletedAt(now);
        } else if (outcome.retryable() && delivery.getAttempts() < properties.maxAttempts()) {
            delivery.setStatus(DeliveryStatus.RETRYING);
            delivery.setNextAttemptAt(now.plus(properties.backoffAfter(delivery.getAttempts())));
        } else {
            delivery.setStatus(DeliveryStatus.FAILED);
            delivery.setNextAttemptAt(null);
            delivery.setCompletedAt(now);
        }
    }

    private static byte[] encodeBody(ContentType contentType, String payloadJson) {
        String body = contentType == ContentType.FORM
                ? "payload=" + URLEncoder.encode(payloadJson, StandardCharsets.UTF_8)
                : payloadJson;
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private String readCapped(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        try (in) {
            return new String(in.readNBytes(properties.maxResponseBodyBytes()), StandardCharsets.UTF_8);
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    record Outcome(Map<String, String> requestHeaders, Integer statusCode, String responseBody, String error,
                   long durationMs, boolean retryable) {

        static Outcome response(Map<String, String> headers, int status, String body, long durationMs) {
            boolean retryable = status >= 500 || status == 408 || status == 429;
            return new Outcome(headers, status, body, null, durationMs, retryable);
        }

        static Outcome failure(Map<String, String> headers, String error, boolean retryable, long durationMs) {
            return new Outcome(headers, null, null, error, durationMs, retryable);
        }

        boolean succeeded() {
            return statusCode != null && statusCode >= 200 && statusCode < 300;
        }
    }
}
