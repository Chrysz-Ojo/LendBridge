package com.lendbridge.investment.messaging;

import com.lendbridge.investment.fundingsaga.RepaymentAssembler;
import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.wallet.avro.WalletRepaymentAccountConfirmed;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Repayment saga's second join input — see {@link RepaymentAssembler}. */
@Component
@RequiredArgsConstructor
public class WalletRepaymentAccountConfirmedEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(WalletRepaymentAccountConfirmedEventConsumer.class);

  private final RepaymentAssembler repaymentAssembler;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "wallet.repayment-account.confirmed", groupId = "investment-service")
  @Transactional
  public void onAccountConfirmed(
      ConsumerRecord<String, WalletRepaymentAccountConfirmed> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed wallet.repayment-account.confirmed event {}", eventId);
      return;
    }

    WalletRepaymentAccountConfirmed payload = record.value();
    UUID instalmentId = UUID.fromString(payload.getInstalmentId().toString());
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    UUID borrowerLedgerAccountId =
        payload.getBorrowerLedgerAccountId() != null
            ? UUID.fromString(payload.getBorrowerLedgerAccountId().toString())
            : null;
    repaymentAssembler.onAccountConfirmed(
        instalmentId, loanId, payload.getSuccess(), borrowerLedgerAccountId, payload.getFailureReason());

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
