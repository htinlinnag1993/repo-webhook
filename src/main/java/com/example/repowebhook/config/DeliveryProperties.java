package com.example.repowebhook.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "webhook.delivery")
public record DeliveryProperties(
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration initialBackoff,
        double backoffMultiplier,
        Duration maxBackoff,
        Duration pendingGrace,
        int maxResponseBodyBytes,
        String userAgent,
        boolean allowPrivateAddresses) {

    /** Backoff before the next attempt, given how many attempts have already been made (1-based). */
    public Duration backoffAfter(int attemptsMade) {
        double millis = initialBackoff.toMillis() * Math.pow(backoffMultiplier, Math.max(0, attemptsMade - 1));
        return millis >= maxBackoff.toMillis() ? maxBackoff : Duration.ofMillis((long) millis);
    }
}
