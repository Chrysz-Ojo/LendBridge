package com.lendbridge.investment.messaging;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.ledger.avro.LedgerLoanDisbursementFailed;
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

/**
 * Closes the one gap ledger-service's own build left open: if posting the disbursement journal
 * entries fails after lender holds were already captured, nothing else restores them — this
 * consumer is that restore, reusing the same {@code investment.disbursement.compensate} event
 * wallet-service and loan-service already handle for the earlier (hold-capture/borrower-account
 * join) failure path.
 */
@Component
@RequiredArgsConstructor
public class LedgerLoanDisbursementFailedEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(LedgerLoanDisbursementFailedEventConsumer.class);

  private final DisbursementAssembler disbursementAssembler;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "ledger.loan-disbursement.failed", groupId = "investment-service")
  @Transactional
  public void onFailed(
      ConsumerRecord<String, LedgerLoanDisbursementFailed> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed ledger.loan-disbursement.failed event {}", eventId);
      return;
    }

    LedgerLoanDisbursementFailed payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    disbursementAssembler.onLedgerDisbursementFailed(loanId);

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
