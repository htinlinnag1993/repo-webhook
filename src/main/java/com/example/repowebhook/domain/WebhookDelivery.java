package com.example.repowebhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One event sent (or to be sent) to one webhook. Doubles as an outbox row: it is written in the same
 * transaction as the event and then picked up by the dispatcher, so events survive restarts.
 */
@Entity
@Table(name = "webhook_deliveries", indexes = {
        @Index(name = "idx_deliveries_webhook", columnList = "webhook_id"),
        @Index(name = "idx_deliveries_due", columnList = "status,next_attempt_at")
})
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Globally unique id sent in the delivery header so receivers can de-duplicate. */
    @Column(nullable = false, unique = true, updatable = false, length = 36)
    private String guid;

    @Column(name = "webhook_id", nullable = false, updatable = false)
    private Long webhookId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false)
    private EventType event;

    private String action;

    @Lob
    @Column(nullable = false, updatable = false)
    private String payload;

    @Column(nullable = false)
    private boolean redelivery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    /** JSON object of the headers sent on the last attempt. */
    @Lob
    private String requestHeaders;

    private Integer responseStatus;

    @Lob
    private String responseBody;

    @Column(length = 1024)
    private String errorMessage;

    private Long durationMs;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastAttemptAt;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    private Instant completedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(Long webhookId, EventType event, String action, String payload, boolean redelivery) {
        this.webhookId = webhookId;
        this.event = event;
        this.action = action;
        this.payload = payload;
        this.redelivery = redelivery;
    }

    @PrePersist
    void onCreate() {
        if (guid == null) {
            guid = UUID.randomUUID().toString();
        }
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getGuid() { return guid; }
    public Long getWebhookId() { return webhookId; }
    public EventType getEvent() { return event; }
    public String getAction() { return action; }
    public String getPayload() { return payload; }
    public boolean isRedelivery() { return redelivery; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public String getRequestHeaders() { return requestHeaders; }
    public void setRequestHeaders(String requestHeaders) { this.requestHeaders = requestHeaders; }
    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
