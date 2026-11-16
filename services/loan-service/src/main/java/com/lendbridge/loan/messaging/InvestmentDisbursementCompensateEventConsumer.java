package com.lendbridge.loan.messaging;

import com.lendbridge.investment.avro.InvestmentDisbursementCompensate;
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

/** The funding saga's one compensating action, from the loan's side: investment-service
 * publishes this topic for both the ways disbursement can fail (the hold-capture/borrower-account
 * join, or the ledger posting itself) — either way, this loan reverts {@code DISBURSING ->
 * FUNDED}. */
@Component
@RequiredArgsConstructor
public class InvestmentDisbursementCompensateEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(InvestmentDisbursementCompensateEventConsumer.class);

  private final LoanService loanService;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.disbursement.compensate", groupId = "loan-service")
  @Transactional
  public void onCompensate(
      ConsumerRecord<String, InvestmentDisbursementCompensate> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.disbursement.compensate event {}", eventId);
      return;
    }

    InvestmentDisbursementCompensate payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    loanService.revertToFunded(loanId);

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
