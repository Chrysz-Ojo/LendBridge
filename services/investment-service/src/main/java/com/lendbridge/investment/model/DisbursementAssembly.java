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
 * The funding saga's aggregator (keyed by loanId): joins three independently-arriving legs —
 * {@code loan.agreement.signed} (origination fee), {@code wallet.holds.capture-assembled}, and
 * {@code wallet.borrower-account.assembled} — before investment-service acts. Each field is
 * {@code null}/{@code false} until its leg arrives; whichever leg arrives last finds the other two
 * already present and triggers the action. Deleted once acted on, so a row's mere existence means
 * "saga in flight."
 */
@Entity
@Table(name = "disbursement_assembly")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisbursementAssembly {

  @Id
  @Column(name = "loan_id")
  private UUID loanId;

  @Column(name = "origination_fee_kobo")
  private Long originationFeeKobo;

  @Column(name = "signed_received", nullable = false)
  private boolean signedReceived;

  @Column(name = "holds_success")
  private Boolean holdsSuccess;

  @Column(name = "holds_failure_reason")
  private String holdsFailureReason;

  @Column(name = "borrower_account_success")
  private Boolean borrowerAccountSuccess;

  @Column(name = "borrower_ledger_account_id")
  private UUID borrowerLedgerAccountId;

  @Column(name = "borrower_account_failure_reason")
  private String borrowerAccountFailureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** All three legs have reported in, whichever way each turned out. */
  public boolean isComplete() {
    return signedReceived && holdsSuccess != null && borrowerAccountSuccess != null;
  }

  public boolean isFullySuccessful() {
    return Boolean.TRUE.equals(holdsSuccess) && Boolean.TRUE.equals(borrowerAccountSuccess);
  }
}
