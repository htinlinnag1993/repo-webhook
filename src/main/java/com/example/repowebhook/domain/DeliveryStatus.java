package com.example.repowebhook.domain;

public enum DeliveryStatus {
    /** Created, waiting for its first attempt. */
    PENDING,
    /** Claimed by a worker; an HTTP request is in flight. */
    IN_PROGRESS,
    /** Last attempt failed with a retryable error; will be retried at {@code nextAttemptAt}. */
    RETRYING,
    /** Subscriber answered with a 2xx. */
    SUCCEEDED,
    /** Gave up: non-retryable response or attempts exhausted. */
    FAILED
}
