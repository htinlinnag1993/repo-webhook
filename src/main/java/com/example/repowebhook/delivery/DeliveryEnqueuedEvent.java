package com.example.repowebhook.delivery;

/** Published when a delivery row is created; handled after the surrounding transaction commits. */
public record DeliveryEnqueuedEvent(long deliveryId) {
}
