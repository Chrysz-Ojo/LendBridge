package com.lendbridge.payment.repository;

import com.lendbridge.payment.model.ProviderName;
import com.lendbridge.payment.model.ProviderTransaction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderTransactionRepository extends JpaRepository<ProviderTransaction, UUID> {

  List<ProviderTransaction> findAllByPaymentIntentIdOrderByCreatedAtAsc(UUID paymentIntentId);

  List<ProviderTransaction> findAllByPayoutIdOrderByCreatedAtAsc(UUID payoutId);

  Optional<ProviderTransaction> findByIdempotencyKey(String idempotencyKey);

  List<ProviderTransaction> findAllByProviderAndCreatedAtBetween(
      ProviderName provider, Instant from, Instant to);
}
