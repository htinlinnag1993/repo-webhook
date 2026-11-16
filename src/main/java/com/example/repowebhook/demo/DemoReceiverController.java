package com.example.repowebhook.demo;

import com.example.repowebhook.delivery.PayloadSigner;
import com.example.repowebhook.delivery.WebhookHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A sample subscriber, so you can try the full flow against this same app. Also a reference for what a
 * receiver should do: verify the signature over the raw body, de-duplicate by delivery GUID, answer fast.
 * <p>
 * Register a hook with {@code "url": "http://localhost:8080/demo/receiver", "secret": "dev-secret"}.
 */
@RestController
@RequestMapping("/demo/receiver")
@ConditionalOnProperty(prefix = "demo.receiver", name = "enabled", havingValue = "true")
public class DemoReceiverController {

    private static final Logger log = LoggerFactory.getLogger(DemoReceiverController.class);
    private static final int MAX_KEPT = 50;

    private final String secret;
    private final Deque<Map<String, Object>> received = new ArrayDeque<>();

    public DemoReceiverController(@Value("${demo.receiver.secret:}") String secret) {
        this.secret = secret;
    }

    @PostMapping
    public ResponseEntity<String> receive(@RequestBody(required = false) byte[] body,
                                          @RequestHeader(value = WebhookHeaders.EVENT, required = false) String event,
                                          @RequestHeader(value = WebhookHeaders.DELIVERY, required = false) String deliveryId,
                                          @RequestHeader(value = WebhookHeaders.SIGNATURE_256, required = false) String signature) {
        byte[] raw = body == null ? new byte[0] : body;
        if (!secret.isEmpty() && !PayloadSigner.verify(secret, raw, signature)) {
            log.warn("Rejected delivery {}: bad or missing signature", deliveryId);
            return ResponseEntity.status(401).body("invalid signature");
        }

        log.info("Received '{}' delivery {} ({} bytes)", event, deliveryId, raw.length);
        synchronized (received) {
            if (received.size() == MAX_KEPT) {
                received.removeLast();
            }
            received.addFirst(Map.of(
                    "received_at", Instant.now().toString(),
                    "event", String.valueOf(event),
                    "delivery", String.valueOf(deliveryId),
                    "body", new String(raw, StandardCharsets.UTF_8)));
        }
        return ResponseEntity.ok("ok");
    }

    /** Most recent deliveries this receiver accepted. */
    @GetMapping
    public List<Map<String, Object>> recent() {
        synchronized (received) {
            return List.copyOf(received);
        }
    }
}
