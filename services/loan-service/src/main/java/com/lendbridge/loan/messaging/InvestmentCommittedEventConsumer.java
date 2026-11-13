package com.lendbridge.loan.messaging;

import com.lendbridge.investment.avro.InvestmentCommitted;
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

/** Funding saga Stage 1: tracks the running funded total on the loan's own row. */
@Component
@RequiredArgsConstructor
public class InvestmentCommittedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentCommittedEventConsumer.class);

  private final LoanService loanService;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.committed", groupId = "loan-service")
  @Transactional
  public void onCommitted(
      ConsumerRecord<String, InvestmentCommitted> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.committed event {}", eventId);
      return;
    }

    InvestmentCommitted payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    loanService.recordCommitment(loanId, payload.getAmountKobo());

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
