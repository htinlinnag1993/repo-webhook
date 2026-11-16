package com.example.repowebhook.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/** A subscriber endpoint registered on a repository ({@code owner/repo}). */
@Entity
@Table(name = "webhooks", indexes = @Index(name = "idx_webhooks_repo", columnList = "owner,repo"))
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private String repo;

    @Column(nullable = false, length = 2048)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentType contentType = ContentType.JSON;

    /**
     * Shared secret used to sign payloads. Stored as-is because it must be recoverable to compute the
     * HMAC; encrypt it at rest (e.g. JPA AttributeConverter + KMS) before going to production.
     */
    private String secret;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "webhook_events", joinColumns = @JoinColumn(name = "webhook_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private Set<EventType> events = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean subscribesTo(EventType event) {
        return events.contains(EventType.WILDCARD) || events.contains(event);
    }

    public boolean hasSecret() {
        return secret != null && !secret.isBlank();
    }

    public Long getId() { return id; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public ContentType getContentType() { return contentType; }
    public void setContentType(ContentType contentType) { this.contentType = contentType; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Set<EventType> getEvents() { return events; }
    public void setEvents(Set<EventType> events) { this.events = events.isEmpty() ? new HashSet<>() : EnumSet.copyOf(events); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
