package com.lendbridge.reporting.model;

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

/** One row per {@code loan.submitted}/{@code loan.listed}/{@code loan.delisted} event — a full
 * event history per loan (not just current state), backing the loan-pipeline-funnel report. */
@Entity
@Table(name = "loan_pipeline_fact")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanPipelineFact {

  @Id private UUID id;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "borrower_user_id", nullable = false)
  private UUID borrowerUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private LoanPipelineStatus status;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "tenor_months", nullable = false)
  private int tenorMonths;

  @Column(name = "interest_rate_bps", nullable = false)
  private int interestRateBps;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;
}
