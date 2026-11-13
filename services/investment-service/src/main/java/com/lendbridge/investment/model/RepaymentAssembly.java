package com.lendbridge.investment.model;

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

/**
 * The repayment saga's aggregator (keyed by instalmentId): joins {@code loan.repayment.requested}
 * (the amount due) and {@code wallet.repayment-account.confirmed} before investment-service acts.
 * Same shape as {@link DisbursementAssembly}, deliberately a second concrete table rather than one
 * generalized table — only two known uses.
 */
@Entity
@Table(name = "repayment_assembly")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepaymentAssembly {

  @Id
  @Column(name = "instalment_id")
  private UUID instalmentId;

  @Column(name = "loan_id", nullable = false)
  private UUID loanId;

  @Column(name = "total_kobo")
  private Long totalKobo;

  @Column(name = "requested_received", nullable = false)
  private boolean requestedReceived;

  @Column(name = "account_confirmed")
  private Boolean accountConfirmed;

  @Column(name = "borrower_ledger_account_id")
  private UUID borrowerLedgerAccountId;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public boolean isComplete() {
    return requestedReceived && accountConfirmed != null;
  }
}
