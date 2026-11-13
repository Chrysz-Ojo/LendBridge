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

/** PRD E3-S5's "holdings" — one row per commitment once its loan disburses (funding saga Stage
 * 9), created from {@code loan.disbursed}. No portfolio-performance screen yet; just the data
 * existing, since nothing can report on performance until repayments happen. */
@Entity
@Table(name = "holding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding {

  @Id private UUID id;

  @Column(name = "lender_user_id", nullable = false)
  private UUID lenderUserId;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private HoldingStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
