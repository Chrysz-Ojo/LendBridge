package com.lendbridge.payment.repository;

import com.lendbridge.payment.model.Payout;
import com.lendbridge.payment.model.ProviderName;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

  Optional<Payout> findByIdempotencyKey(String idempotencyKey);

  Optional<Payout> findByProviderAndProviderReference(
      ProviderName provider, String providerReference);

  @Query(
      "select coalesce(sum(p.amountKobo), 0) from Payout p where p.userId = :userId and"
          + " p.createdAt >= :since and p.status in ('PENDING', 'PROCESSING', 'SUCCESSFUL')")
  long sumWithdrawalsByUserIdSince(@Param("userId") UUID userId, @Param("since") Instant since);
}
