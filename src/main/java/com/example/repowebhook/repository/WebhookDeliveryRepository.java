package com.example.repowebhook.repository;

import com.example.repowebhook.domain.DeliveryStatus;
import com.example.repowebhook.domain.WebhookDelivery;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    Page<WebhookDelivery> findByWebhookIdOrderByIdDesc(Long webhookId, Pageable pageable);

    Optional<WebhookDelivery> findByIdAndWebhookId(Long id, Long webhookId);

    List<WebhookDelivery> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            Collection<DeliveryStatus> statuses, Instant now);

    /**
     * Atomically moves a delivery into {@code to} if it is currently in one of {@code from}.
     * Returns 1 if this caller won the claim, 0 otherwise — this is what prevents double sends when
     * the async listener and the retry scheduler race for the same row.
     */
    @Modifying
    @Query("update WebhookDelivery d set d.status = :to where d.id = :id and d.status in :from")
    int transition(@Param("id") Long id, @Param("from") Collection<DeliveryStatus> from, @Param("to") DeliveryStatus to);

    @Modifying
    @Query("delete from WebhookDelivery d where d.webhookId = :webhookId")
    int deleteByWebhookId(@Param("webhookId") Long webhookId);
}
