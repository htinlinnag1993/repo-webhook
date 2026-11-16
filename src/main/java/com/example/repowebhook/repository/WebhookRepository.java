package com.example.repowebhook.repository;

import com.example.repowebhook.domain.Webhook;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByOwnerAndRepoOrderByIdAsc(String owner, String repo);

    List<Webhook> findByOwnerAndRepoAndActiveTrue(String owner, String repo);

    Optional<Webhook> findByIdAndOwnerAndRepo(Long id, String owner, String repo);
}
