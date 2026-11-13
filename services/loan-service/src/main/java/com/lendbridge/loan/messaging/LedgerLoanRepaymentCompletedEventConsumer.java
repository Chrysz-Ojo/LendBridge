package com.lendbridge.loan.messaging;

import com.lendbridge.ledger.avro.LedgerLoanRepaymentCompleted;
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

/** Repayment saga's final step: the one journal entry has been attempted — {@code success}
 * distinguishes marking the instalment Paid from reverting it back to Pending. */
@Component
@RequiredArgsConstructor
public class LedgerLoanRepaymentCompletedEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(LedgerLoanRepaymentCompletedEventConsumer.class);

  private final LoanService loanService;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "ledger.loan-repayment.completed", groupId = "loan-service")
  @Transactional
  public void onCompleted(
      ConsumerRecord<String, LedgerLoanRepaymentCompleted> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed ledger.loan-repayment.completed event {}", eventId);
      return;
    }

    LedgerLoanRepaymentCompleted payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    UUID instalmentId = UUID.fromString(payload.getInstalmentId().toString());
    if (payload.getSuccess()) {
      loanService.markInstalmentPaid(loanId, instalmentId);
    } else {
      loanService.revertInstalmentToPending(loanId, instalmentId);
    }

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
