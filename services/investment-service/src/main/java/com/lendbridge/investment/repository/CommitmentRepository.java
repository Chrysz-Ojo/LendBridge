package com.lendbridge.investment.repository;

import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommitmentRepository extends JpaRepository<Commitment, UUID> {

  Optional<Commitment> findByIdempotencyKey(String idempotencyKey);

  /** All active commitments against one loan — summed in the service layer to validate the
   * remaining-unfunded amount (PRD E4-S1). A derived list + in-memory sum, not a {@code @Query}
   * aggregate: Phase 1's dataset is small enough that the simpler form is the right call. */
  List<Commitment> findAllByLoanIdAndStatus(UUID loanId, CommitmentStatus status);

  /** All of one lender's active commitments — summed (and cross-referenced against each loan's
   * borrower via {@code LoanListingRepository}) to enforce the borrower-exposure cap (PRD E3-S3). */
  List<Commitment> findAllByLenderUserIdAndStatus(UUID lenderUserId, CommitmentStatus status);

  List<Commitment> findAllByLenderUserIdOrderByCreatedAtDesc(UUID lenderUserId);
}
