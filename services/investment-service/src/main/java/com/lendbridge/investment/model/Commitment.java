package com.lendbridge.investment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A lender's reservation of funds against one listed loan (PRD E4-S1). {@code holdId} is
 * wallet-service's own hold id, needed to release it on cancel. {@code idempotencyKey} backs
 * {@code IdempotencyOperations.runIdempotently} exactly like wallet-service's own hold creation —
 * a unique constraint on this column is the race backstop. */
@Entity
@Table(name = "commitment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commitment {

  @Id private UUID id;

  @Column(name = "lender_user_id", nullable = false)
  private UUID lenderUserId;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "hold_id", nullable = false)
  private UUID holdId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private CommitmentStatus status;

  @Column(name = "idempotency_key", nullable = false, unique = true)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;
}
