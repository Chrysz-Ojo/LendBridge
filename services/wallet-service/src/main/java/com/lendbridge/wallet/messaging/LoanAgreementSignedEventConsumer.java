package com.lendbridge.wallet.messaging;

import com.lendbridge.loan.avro.LoanAgreementSigned;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import com.lendbridge.wallet.model.ProcessedEvent;
import com.lendbridge.wallet.model.Wallet;
import com.lendbridge.wallet.outbox.KafkaTopics;
import com.lendbridge.wallet.outbox.WalletBorrowerAccountAssembledPayload;
import com.lendbridge.wallet.repository.ProcessedEventRepository;
import com.lendbridge.wallet.service.WalletService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Funding saga Stage 3's wallet-side leg: ensures the borrower's wallet has a provisioned ledger
 * account (same lazy-provisioning logic {@code WalletServiceImpl.getOrCreateWallet} already uses
 * on first balance read, just invoked here instead of waiting for that). Not itself
 * {@code @Transactional} — {@code getOrCreateWallet} has its own transaction boundary that must
 * commit or roll back independently of the outbox write that reports the outcome, same reasoning
 * as {@code InvestmentDisbursementReadyEventConsumer} in ledger-service.
 */
@Component
@RequiredArgsConstructor
public class LoanAgreementSignedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanAgreementSignedEventConsumer.class);

  private final WalletService walletService;
  private final ProcessedEventRepository processedEventRepository;
  private final OutboxEventWriter outboxEventWriter;

  @KafkaListener(topics = "loan.agreement.signed", groupId = "wallet-service")
  public void onSigned(
      ConsumerRecord<String, LoanAgreementSigned> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.agreement.signed event {}", eventId);
      return;
    }

    LoanAgreementSigned payload = record.value();
    String loanId = payload.getLoanId().toString();
    UUID borrowerUserId = UUID.fromString(payload.getBorrowerUserId().toString());
    Instant now = Instant.now();

    boolean success;
    String borrowerLedgerAccountId = null;
    String failureReason = null;
    try {
      Wallet wallet = walletService.getOrCreateWallet(borrowerUserId);
      borrowerLedgerAccountId = wallet.getLedgerAccountId().toString();
      success = true;
    } catch (Exception e) {
      log.error("Failed to provision borrower ledger account for loan {}", loanId, e);
      success = false;
      failureReason = e.getMessage();
    }

    outboxEventWriter.write(
        "Loan",
        loanId,
        KafkaTopics.WALLET_BORROWER_ACCOUNT_ASSEMBLED,
        new WalletBorrowerAccountAssembledPayload(
            loanId, success, borrowerLedgerAccountId, failureReason, now.toEpochMilli()));

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
