package com.lendbridge.investment.messaging;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.wallet.avro.WalletBorrowerAccountAssembled;
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

/** Funding saga Stage 5's second join input — see {@link DisbursementAssembler}. */
@Component
@RequiredArgsConstructor
public class WalletBorrowerAccountAssembledEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(WalletBorrowerAccountAssembledEventConsumer.class);

  private final DisbursementAssembler disbursementAssembler;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "wallet.borrower-account.assembled", groupId = "investment-service")
  @Transactional
  public void onBorrowerAccountAssembled(
      ConsumerRecord<String, WalletBorrowerAccountAssembled> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed wallet.borrower-account.assembled event {}", eventId);
      return;
    }

    WalletBorrowerAccountAssembled payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    UUID borrowerLedgerAccountId =
        payload.getBorrowerLedgerAccountId() != null
            ? UUID.fromString(payload.getBorrowerLedgerAccountId().toString())
            : null;
    disbursementAssembler.onBorrowerAccountAssembled(
        loanId, payload.getSuccess(), borrowerLedgerAccountId, payload.getFailureReason());

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
