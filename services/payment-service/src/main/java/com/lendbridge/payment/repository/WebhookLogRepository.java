package com.lendbridge.payment.repository;

import com.lendbridge.payment.model.WebhookLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookLogRepository extends JpaRepository<WebhookLog, UUID> {

  Optional<WebhookLog> findByProviderEventId(String providerEventId);
}
