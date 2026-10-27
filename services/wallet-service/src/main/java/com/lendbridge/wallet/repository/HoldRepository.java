package com.lendbridge.wallet.repository;

import com.lendbridge.wallet.model.Hold;
import com.lendbridge.wallet.model.HoldStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HoldRepository extends JpaRepository<Hold, UUID> {

  Optional<Hold> findByIdempotencyKey(String idempotencyKey);

  List<Hold> findAllByWalletIdAndStatusOrderByCreatedAtDesc(UUID walletId, HoldStatus status);

  @Query("select coalesce(sum(h.amountKobo), 0) from Hold h where h.walletId = :walletId and h.status = 'ACTIVE'")
  long sumActiveHoldsByWalletId(@Param("walletId") UUID walletId);
}
