package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.model.TransactionPin;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionPinRepository extends JpaRepository<TransactionPin, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from TransactionPin p where p.userId = :userId")
  Optional<TransactionPin> findByUserIdForUpdate(@Param("userId") UUID userId);
}
