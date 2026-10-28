package com.lendbridge.kyc.repository;

import com.lendbridge.kyc.model.VerificationCase;
import com.lendbridge.kyc.model.VerificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationCaseRepository extends JpaRepository<VerificationCase, UUID> {

  /**
   * The "open case" the partial unique index in {@code 001-verification-case.yaml} enforces at
   * most one of per user — what {@code IdempotencyOperations.runIdempotently} keys off of in
   * {@code VerificationServiceImpl.startVerification} (see the approved plan's design decisions).
   */
  @Query(
      "select v from VerificationCase v where v.userId = :userId "
          + "and v.status in (com.lendbridge.kyc.model.VerificationStatus.PENDING, "
          + "com.lendbridge.kyc.model.VerificationStatus.NEEDS_MORE_INFO)")
  Optional<VerificationCase> findOpenCaseByUserId(@Param("userId") UUID userId);

  Optional<VerificationCase> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

  List<VerificationCase> findAllByStatusAndReviewRequiredTrueOrderByCreatedAtAsc(
      VerificationStatus status);
}
