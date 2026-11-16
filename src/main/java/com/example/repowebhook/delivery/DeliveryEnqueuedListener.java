package com.example.repowebhook.delivery;

import com.example.repowebhook.config.DeliveryConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fast path: attempts a delivery right after the row is committed. If this is lost (crash, full queue),
 * {@link DeliveryRetryScheduler} sweeps the row up after {@code pending-grace}.
 */
@Component
public class DeliveryEnqueuedListener {

    private final WebhookDispatcher dispatcher;

    public DeliveryEnqueuedListener(WebhookDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Async(DeliveryConfig.WEBHOOK_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDeliveryEnqueued(DeliveryEnqueuedEvent event) {
        dispatcher.attempt(event.deliveryId());
    }
}
