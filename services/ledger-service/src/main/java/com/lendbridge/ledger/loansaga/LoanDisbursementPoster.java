package com.lendbridge.ledger.loansaga;

import com.lendbridge.ledger.ClearingAccounts;
import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import com.lendbridge.ledger.dto.PostingRequest;
import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.outbox.KafkaTopics;
import com.lendbridge.ledger.outbox.LedgerLoanDisbursementCompletedPayload;
import com.lendbridge.ledger.outbox.LedgerLoanDisbursementFailedPayload;
import com.lendbridge.ledger.service.JournalEntryService;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two disbursement journal entries (lenders -> escrow, escrow -> borrower + fee) post in one
 * local transaction via {@code post(...)} — the existing, unmodified
 * {@link JournalEntryService#postJournalEntry} is called twice, so if the second entry's
 * validation fails, the whole transaction (including the first entry) rolls back for free via
 * normal DB semantics; nothing partial ever commits. {@code post(...)} deliberately lets any
 * exception propagate — the calling consumer catches it <i>outside</i> this method's transactional
 * boundary and calls {@link #recordFailure} as a genuinely separate transaction, so the failure
 * outbox event survives even though the posting attempt's own transaction rolled back.
 *
 * <p>A separate bean (not a second method on the consumer) is required here: Spring's
 * {@code @Transactional} proxying does not intercept self-invocation, so {@code post} and
 * {@code recordFailure} must be called through this bean's proxy from another bean to get their
 * own transaction boundaries at all.
 */
@Component
public class LoanDisbursementPoster {

  private final JournalEntryService journalEntryService;
  private final OutboxEventWriter outboxEventWriter;

  public LoanDisbursementPoster(JournalEntryService journalEntryService, OutboxEventWriter outboxEventWriter) {
    this.journalEntryService = journalEntryService;
    this.outboxEventWriter = outboxEventWriter;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void post(
      UUID loanId,
      List<DisbursementLeg> legs,
      long totalKobo,
      UUID borrowerLedgerAccountId,
      long originationFeeKobo) {
    List<PostingRequest> entryAPostings = new ArrayList<>();
    for (DisbursementLeg leg : legs) {
      entryAPostings.add(new PostingRequest(leg.getLenderLedgerAccountId(), DebitCredit.DEBIT, leg.getAmountKobo()));
    }
    entryAPostings.add(new PostingRequest(ClearingAccounts.LOAN_ESCROW_NGN, DebitCredit.CREDIT, totalKobo));
    PostJournalEntryRequest entryA = new PostJournalEntryRequest();
    entryA.setCurrency("NGN");
    entryA.setReference(loanId.toString());
    entryA.setDescription("loan.disbursement.lenders-to-escrow");
    entryA.setPostings(entryAPostings);
    journalEntryService.postJournalEntry(entryA, "loan-disbursement-a-" + loanId);

    long borrowerShareKobo = totalKobo - originationFeeKobo;
    List<PostingRequest> entryBPostings =
        List.of(
            new PostingRequest(ClearingAccounts.LOAN_ESCROW_NGN, DebitCredit.DEBIT, totalKobo),
            new PostingRequest(borrowerLedgerAccountId, DebitCredit.CREDIT, borrowerShareKobo),
            new PostingRequest(ClearingAccounts.PLATFORM_FEE_REVENUE_NGN, DebitCredit.CREDIT, originationFeeKobo));
    PostJournalEntryRequest entryB = new PostJournalEntryRequest();
    entryB.setCurrency("NGN");
    entryB.setReference(loanId.toString());
    entryB.setDescription("loan.disbursement.escrow-to-borrower");
    entryB.setPostings(entryBPostings);
    journalEntryService.postJournalEntry(entryB, "loan-disbursement-b-" + loanId);

    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.LEDGER_LOAN_DISBURSEMENT_COMPLETED,
        new LedgerLoanDisbursementCompletedPayload(loanId.toString(), totalKobo, Instant.now().toEpochMilli()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID loanId, String reason) {
    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.LEDGER_LOAN_DISBURSEMENT_FAILED,
        new LedgerLoanDisbursementFailedPayload(loanId.toString(), reason, Instant.now().toEpochMilli()));
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DisbursementLeg {
    private UUID lenderLedgerAccountId;
    private long amountKobo;
  }
}
