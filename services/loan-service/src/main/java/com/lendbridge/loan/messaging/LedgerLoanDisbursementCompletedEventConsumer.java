package com.lendbridge.loan.messaging;

import com.lendbridge.ledger.avro.LedgerLoanDisbursementCompleted;
import com.lendbridge.loan.model.ProcessedEvent;
import com.lendbridge.loan.repository.ProcessedEventRepository;
import com.lendbridge.loan.service.LoanService;
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

/** Funding saga Stage 8: both disbursement journal entries have posted — persists the repayment
 * schedule and transitions the loan to {@code ACTIVE}. */
@Component
@RequiredArgsConstructor
public class LedgerLoanDisbursementCompletedEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(LedgerLoanDisbursementCompletedEventConsumer.class);

  private final LoanService loanService;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "ledger.loan-disbursement.completed", groupId = "loan-service")
  @Transactional
  public void onCompleted(
      ConsumerRecord<String, LedgerLoanDisbursementCompleted> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed ledger.loan-disbursement.completed event {}", eventId);
      return;
    }

    LedgerLoanDisbursementCompleted payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    loanService.disburse(loanId);

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
