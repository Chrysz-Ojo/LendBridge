package com.lendbridge.wallet.repository;

import com.lendbridge.wallet.model.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

  Optional<Wallet> findByUserId(UUID userId);

  boolean existsByUserId(UUID userId);

  /** Pessimistic lock so concurrent first-accesses to the same wallet serialize on lazy
   * ledger-account provisioning instead of racing to open two ledger accounts for one wallet. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from Wallet w where w.userId = :userId")
  Optional<Wallet> findByUserIdForUpdate(UUID userId);
}
