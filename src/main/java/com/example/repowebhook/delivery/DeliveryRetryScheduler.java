package com.example.repowebhook.delivery;

import com.example.repowebhook.config.DeliveryConfig;
import com.example.repowebhook.domain.DeliveryStatus;
import com.example.repowebhook.domain.WebhookDelivery;
import com.example.repowebhook.repository.WebhookDeliveryRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically re-dispatches deliveries that are due for a retry, or that were never picked up. */
@Component
public class DeliveryRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetryScheduler.class);
    private static final EnumSet<DeliveryStatus> DUE_STATUSES = EnumSet.of(DeliveryStatus.PENDING, DeliveryStatus.RETRYING);

    private final WebhookDeliveryRepository deliveries;
    private final WebhookDispatcher dispatcher;
    private final TaskExecutor executor;

    public DeliveryRetryScheduler(WebhookDeliveryRepository deliveries, WebhookDispatcher dispatcher,
                                  @Qualifier(DeliveryConfig.WEBHOOK_EXECUTOR) TaskExecutor executor) {
        this.deliveries = deliveries;
        this.dispatcher = dispatcher;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${webhook.delivery.retry-poll-interval:PT5S}")
    public void dispatchDueDeliveries() {
        List<WebhookDelivery> due = deliveries
                .findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(DUE_STATUSES, Instant.now());
        for (WebhookDelivery delivery : due) {
            long id = delivery.getId();
            try {
                executor.execute(() -> dispatcher.attempt(id));
            } catch (RejectedExecutionException e) {
                log.warn("Delivery queue full; {} remaining deliveries will be retried next poll", due.size());
                return;
            }
        }
    }
}
