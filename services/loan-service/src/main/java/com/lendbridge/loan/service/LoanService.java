package com.lendbridge.loan.service;

import com.lendbridge.loan.dto.AgreementResponse;
import com.lendbridge.loan.dto.CreateLoanRequest;
import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.RepaymentRequest;
import com.lendbridge.loan.dto.ScheduleResponse;
import java.util.List;
import java.util.UUID;

public interface LoanService {

  LoanResponse create(UUID borrowerUserId, CreateLoanRequest request);

  LoanResponse submit(UUID borrowerUserId, UUID loanId);

  LoanResponse cancel(UUID borrowerUserId, UUID loanId);

  LoanResponse getOwn(UUID borrowerUserId, UUID loanId);

  List<LoanResponse> listOwn(UUID borrowerUserId);

  ScheduleResponse getSchedule(UUID borrowerUserId, UUID loanId);

  AgreementResponse generateAgreement(UUID borrowerUserId, UUID loanId);

  /** Funding saga Stage 2: validates {@code status == FUNDED}, computes the origination fee,
   * transitions to {@code DISBURSING}, publishes {@code loan.agreement.signed}. */
  LoanResponse signAgreement(UUID borrowerUserId, UUID loanId);

  /** Funding saga Stage 1, triggered by {@code investment.committed}: adds to the running funded
   * total, transitioning {@code LISTED -> FUNDING} on the first commitment and
   * {@code FUNDING -> FUNDED} once fully committed. A no-op if the loan isn't in a state that can
   * still accept commitments (defensive against a late/out-of-order event). */
  void recordCommitment(UUID loanId, long amountKobo);

  /** Funding saga Stage 1, triggered by {@code investment.cancelled}: the inverse of
   * {@link #recordCommitment} — subtracts from the running funded total, reverting
   * {@code FUNDED -> FUNDING} if the loan drops back below 100% committed. */
  void recordCommitmentCancelled(UUID loanId, long amountKobo);

  /** The funding saga's one compensating action, triggered by
   * {@code investment.disbursement.compensate}: reverts {@code DISBURSING -> FUNDED}. A no-op if
   * the loan isn't {@code DISBURSING} (idempotent against redelivery, or an event that arrives
   * after some other path already resolved this loan). */
  void revertToFunded(UUID loanId);

  /** Funding saga Stage 8, triggered by {@code ledger.loan-disbursement.completed}: persists the
   * repayment schedule (previously only ever a request-time preview), transitions
   * {@code DISBURSING -> ACTIVE}, publishes {@code loan.disbursed}. A no-op if the loan isn't
   * {@code DISBURSING} (idempotent against redelivery). */
  void disburse(UUID loanId);

  /** Repayment saga, user-initiated: validates {@code status == ACTIVE} and {@code method ==
   * WALLET}, flips the next outstanding instalment {@code PENDING -> REQUESTED} (the status-guard
   * that makes a retry fail fast rather than double-publish), publishes
   * {@code loan.repayment.requested}. */
  LoanResponse requestRepayment(UUID borrowerUserId, UUID loanId, RepaymentRequest request);

  /** Triggered by {@code ledger.loan-repayment.completed} on success: flips the instalment
   * {@code REQUESTED -> PAID}; if every instalment on the loan is now {@code PAID}, transitions
   * {@code ACTIVE -> REPAID} and publishes {@code loan.repaid}. A no-op if the instalment isn't
   * {@code REQUESTED} (idempotent against redelivery). */
  void markInstalmentPaid(UUID loanId, UUID instalmentId);

  /** The repayment saga's one compensating action, triggered by either
   * {@code ledger.loan-repayment.completed} (success=false) or {@code
   * investment.repayment.failed}: reverts the instalment {@code REQUESTED -> PENDING} so the
   * borrower can retry. A no-op if the instalment isn't {@code REQUESTED}. */
  void revertInstalmentToPending(UUID loanId, UUID instalmentId);
}
