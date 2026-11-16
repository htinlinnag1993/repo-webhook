package com.example.repowebhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end: register a hook pointing at the built-in demo receiver, publish events, and check the
 * deliveries were signed correctly (the receiver returns 401 on a bad signature, which would show up as FAILED).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"demo.receiver.enabled=true", "demo.receiver.secret=test-secret"})
class WebhookFlowIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    @Test
    void registersHookAndDeliversSignedEvents() throws Exception {
        String hooks = "/api/repos/octo/hello/hooks";
        ResponseEntity<JsonNode> created = http.postForEntity(hooks, Map.of(
                "events", List.of("push"),
                "config", Map.of(
                        "url", "http://localhost:" + port + "/demo/receiver",
                        "content_type", "json",
                        "secret", "test-secret")), JsonNode.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode hook = created.getBody();
        long hookId = hook.get("id").asLong();
        assertThat(hook.at("/config/secret").asText()).isEqualTo("********");
        assertThat(hook.get("events")).hasSize(1);

        // Subscribed event -> one delivery queued
        ResponseEntity<JsonNode> push = http.postForEntity("/api/repos/octo/hello/events", Map.of(
                "event", "push",
                "payload", Map.of("ref", "refs/heads/main", "after", "abc123")), JsonNode.class);
        assertThat(push.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(push.getBody().get("deliveries")).hasSize(1);

        // Unsubscribed event -> nothing queued
        ResponseEntity<JsonNode> issues = http.postForEntity("/api/repos/octo/hello/events",
                Map.of("event", "issues", "action", "opened"), JsonNode.class);
        assertThat(issues.getBody().get("deliveries")).isEmpty();

        // Both the automatic ping and the push should be delivered successfully
        String deliveriesUrl = hooks + "/" + hookId + "/deliveries";
        JsonNode deliveries = awaitJson(deliveriesUrl,
                list -> list.size() == 2 && allMatch(list, d -> "SUCCEEDED".equals(d.get("status").asText())));
        assertThat(deliveries.get(0).get("event").asText()).isEqualTo("push");
        assertThat(deliveries.get(1).get("event").asText()).isEqualTo("ping");

        long pushDeliveryId = deliveries.get(0).get("id").asLong();
        JsonNode detail = http.getForObject(deliveriesUrl + "/" + pushDeliveryId, JsonNode.class);
        assertThat(detail.get("status_code").asInt()).isEqualTo(200);
        assertThat(detail.at("/request/payload/ref").asText()).isEqualTo("refs/heads/main");
        assertThat(detail.at("/request/payload/repository/full_name").asText()).isEqualTo("octo/hello");
        assertThat(detail.at("/request/headers/X-Hub-Signature-256").asText()).startsWith("sha256=");

        // Redelivery creates a new delivery with a new GUID
        ResponseEntity<JsonNode> redelivered = http.postForEntity(
                deliveriesUrl + "/" + pushDeliveryId + "/attempts", null, JsonNode.class);
        assertThat(redelivered.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(redelivered.getBody().get("redelivery").asBoolean()).isTrue();
        assertThat(redelivered.getBody().get("guid").asText()).isNotEqualTo(detail.get("guid").asText());
    }

    @Test
    void wrongSecretResultsInFailedDelivery() throws Exception {
        String hooks = "/api/repos/octo/wrong-secret/hooks";
        JsonNode hook = http.postForObject(hooks, Map.of("config", Map.of(
                "url", "http://localhost:" + port + "/demo/receiver",
                "secret", "not-the-receivers-secret")), JsonNode.class);

        JsonNode deliveries = awaitJson(hooks + "/" + hook.get("id").asLong() + "/deliveries",
                list -> list.size() == 1 && "FAILED".equals(list.get(0).get("status").asText()));
        // 401 is a non-retryable client error, so it fails after a single attempt
        assertThat(deliveries.get(0).get("status_code").asInt()).isEqualTo(401);
        assertThat(deliveries.get(0).get("attempts").asInt()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidRegistrations() {
        String hooks = "/api/repos/octo/hello/hooks";

        ResponseEntity<JsonNode> badScheme = http.postForEntity(hooks,
                Map.of("config", Map.of("url", "ftp://example.com/hook")), JsonNode.class);
        assertThat(badScheme.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<JsonNode> missingConfig = http.postForEntity(hooks, Map.of("events", List.of("push")), JsonNode.class);
        assertThat(missingConfig.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<JsonNode> unknownEvent = http.postForEntity(hooks, Map.of(
                "events", List.of("not_an_event"),
                "config", Map.of("url", "https://example.com/hook")), JsonNode.class);
        assertThat(unknownEvent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> missingHook = http.getForEntity(hooks + "/999999", JsonNode.class);
        assertThat(missingHook.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private JsonNode awaitJson(String url, Predicate<JsonNode> condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        JsonNode last = null;
        while (Instant.now().isBefore(deadline)) {
            last = http.getForObject(url, JsonNode.class);
            if (last != null && condition.test(last)) {
                return last;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Condition not met within 10s; last response: " + last);
    }

    private static boolean allMatch(JsonNode array, Predicate<JsonNode> predicate) {
        for (JsonNode node : array) {
            if (!predicate.test(node)) {
                return false;
            }
        }
        return true;
    }
}
