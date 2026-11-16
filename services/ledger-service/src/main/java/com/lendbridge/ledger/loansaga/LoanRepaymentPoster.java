package com.lendbridge.ledger.loansaga;

import com.lendbridge.ledger.dto.PostJournalEntryRequest;
import com.lendbridge.ledger.dto.PostingRequest;
import com.lendbridge.ledger.model.DebitCredit;
import com.lendbridge.ledger.outbox.KafkaTopics;
import com.lendbridge.ledger.outbox.LedgerLoanRepaymentCompletedPayload;
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
 * Unlike disbursement, a repayment is a single journal entry (borrower debit, lenders credit pro
 * rata) with no escrow hop — PRD E8-S5 describes repayment allocation as a direct pro-rata split,
 * not a three-party flow, and there's no multi-step atomicity concern a single posting needs
 * protecting from. Same {@code post}/{@code recordFailure} split as
 * {@link LoanDisbursementPoster} regardless, for the same self-invocation-proxying reason and for
 * consistency between the two saga postings.
 */
@Component
public class LoanRepaymentPoster {

  private final JournalEntryService journalEntryService;
  private final OutboxEventWriter outboxEventWriter;

  public LoanRepaymentPoster(JournalEntryService journalEntryService, OutboxEventWriter outboxEventWriter) {
    this.journalEntryService = journalEntryService;
    this.outboxEventWriter = outboxEventWriter;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void post(
      UUID loanId, UUID instalmentId, UUID borrowerLedgerAccountId, List<RepaymentLeg> legs, long totalKobo) {
    List<PostingRequest> postings = new ArrayList<>();
    postings.add(new PostingRequest(borrowerLedgerAccountId, DebitCredit.DEBIT, totalKobo));
    for (RepaymentLeg leg : legs) {
      postings.add(new PostingRequest(leg.getLenderLedgerAccountId(), DebitCredit.CREDIT, leg.getAmountKobo()));
    }
    PostJournalEntryRequest request = new PostJournalEntryRequest();
    request.setCurrency("NGN");
    request.setReference(loanId + ":" + instalmentId);
    request.setDescription("loan.repayment");
    request.setPostings(postings);
    journalEntryService.postJournalEntry(request, "loan-repayment-" + instalmentId);

    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.LEDGER_LOAN_REPAYMENT_COMPLETED,
        new LedgerLoanRepaymentCompletedPayload(
            loanId.toString(), instalmentId.toString(), true, null, Instant.now().toEpochMilli()));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(UUID loanId, UUID instalmentId, String reason) {
    outboxEventWriter.write(
        "Loan",
        loanId.toString(),
        KafkaTopics.LEDGER_LOAN_REPAYMENT_COMPLETED,
        new LedgerLoanRepaymentCompletedPayload(
            loanId.toString(), instalmentId.toString(), false, reason, Instant.now().toEpochMilli()));
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RepaymentLeg {
    private UUID lenderLedgerAccountId;
    private long amountKobo;
  }
}
