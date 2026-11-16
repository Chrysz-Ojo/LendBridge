package com.lendbridge.wallet.messaging;

import com.lendbridge.loan.avro.LoanRepaymentRequested;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.model.ProcessedEvent;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletRepaymentAccountConfirmedPayload;
import com.lendbridge.wallet.repository.ProcessedEventRepository;
import com.lendbridge.wallet.service.WalletService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Repayment saga's wallet-side leg: confirms the borrower's wallet already has a ledger account
 * (it was provisioned once, at signing — see {@link LoanAgreementSignedEventConsumer}), so
 * investment-service has somewhere to tell ledger-service to debit. {@code findWallet}, not
 * {@code getOrCreateWallet}: this is a plain lookup with no provisioning side effect expected at
 * this point in the saga, same reasoning as {@code HoldServiceImpl.releaseHold}.
 */
@Component
@RequiredArgsConstructor
public class LoanRepaymentRequestedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanRepaymentRequestedEventConsumer.class);

  private final WalletService walletService;
  private final ProcessedEventRepository processedEventRepository;
  private final OutboxEventWriter outboxEventWriter;

  @KafkaListener(topics = "loan.repayment.requested", groupId = "wallet-service")
  public void onRepaymentRequested(
      ConsumerRecord<String, LoanRepaymentRequested> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.repayment.requested event {}", eventId);
      return;
    }

    LoanRepaymentRequested payload = record.value();
    String loanId = payload.getLoanId().toString();
    String instalmentId = payload.getInstalmentId().toString();
    UUID borrowerUserId = UUID.fromString(payload.getBorrowerUserId().toString());
    Instant now = Instant.now();

    Optional<Wallet> wallet = walletService.findWallet(borrowerUserId);
    boolean success = wallet.isPresent() && wallet.get().getLedgerAccountId() != null;
    String borrowerLedgerAccountId = success ? wallet.get().getLedgerAccountId().toString() : null;
    String failureReason = success ? null : "Borrower wallet not provisioned";
    if (!success) {
      log.warn("Borrower {} has no provisioned wallet for repayment on loan {}", borrowerUserId, loanId);
    }

    outboxEventWriter.write(
        "Loan",
        loanId,
        KafkaTopics.WALLET_REPAYMENT_ACCOUNT_CONFIRMED,
        new WalletRepaymentAccountConfirmedPayload(
            loanId, instalmentId, success, borrowerLedgerAccountId, failureReason, now.toEpochMilli()));

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
