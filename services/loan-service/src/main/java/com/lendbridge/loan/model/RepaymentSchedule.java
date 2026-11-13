package com.lendbridge.loan.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Funding saga Stage 8: the instalment schedule, persisted for the first time once a loan
 * disburses — previously only ever a request-time preview (see
 * {@code RepaymentScheduleCalculator}, still used to compute these totals). One row per loan. */
@Entity
@Table(name = "repayment_schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentSchedule {

  @Id
  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "total_principal_kobo", nullable = false)
  private long totalPrincipalKobo;

  @Column(name = "total_interest_kobo", nullable = false)
  private long totalInterestKobo;

  @Column(name = "total_repayment_kobo", nullable = false)
  private long totalRepaymentKobo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
