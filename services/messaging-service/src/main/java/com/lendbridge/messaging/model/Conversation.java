package com.lendbridge.messaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Created lazily when an {@code investment.committed} event arrives for a loan this service
 * already has a {@link BorrowerLoanProjection} for — Phase 1 chat eligibility is "committed"
 * only (see ADR 0019; "viewed" eligibility is deferred). One conversation per (loan, lender) pair.
 */
@Entity
@Table(
    name = "conversation",
    uniqueConstraints = @UniqueConstraint(columnNames = {"loan_id", "lender_user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

  @Id private UUID id;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Column(name = "lender_user_id", nullable = false)
  private UUID lenderUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ConversationStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
