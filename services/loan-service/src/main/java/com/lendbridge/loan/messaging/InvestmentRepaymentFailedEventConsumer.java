package com.lendbridge.loan.messaging;

import com.lendbridge.investment.avro.InvestmentRepaymentFailed;
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

/** The repayment join failed before ever reaching the ledger (e.g. the borrower's wallet account
 * couldn't be confirmed) — reverts the instalment back to Pending, same target as a ledger-side
 * failure. */
@Component
@RequiredArgsConstructor
public class InvestmentRepaymentFailedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentRepaymentFailedEventConsumer.class);

  private final LoanService loanService;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.repayment.failed", groupId = "loan-service")
  @Transactional
  public void onFailed(
      ConsumerRecord<String, InvestmentRepaymentFailed> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.repayment.failed event {}", eventId);
      return;
    }

    InvestmentRepaymentFailed payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    UUID instalmentId = UUID.fromString(payload.getInstalmentId().toString());
    loanService.revertInstalmentToPending(loanId, instalmentId);

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
