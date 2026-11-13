package com.lendbridge.investment.messaging;

import com.lendbridge.investment.model.Commitment;
import com.lendbridge.investment.model.CommitmentStatus;
import com.lendbridge.investment.model.Holding;
import com.lendbridge.investment.model.HoldingStatus;
import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.CommitmentRepository;
import com.lendbridge.investment.repository.HoldingRepository;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanDisbursed;
import com.lendbridge.starter.crypto.UuidV7;
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
 * Funding saga Stage 9 (PRD E3-S5): creates one {@link Holding} row per ACTIVE commitment on a
 * now-disbursed loan. No portfolio-performance screen yet; just the data existing.
 */
@Component
@RequiredArgsConstructor
public class LoanDisbursedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanDisbursedEventConsumer.class);

  private final CommitmentRepository commitmentRepository;
  private final HoldingRepository holdingRepository;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "loan.disbursed", groupId = "investment-service")
  @Transactional
  public void onDisbursed(
      ConsumerRecord<String, LoanDisbursed> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.disbursed event {}", eventId);
      return;
    }

    LoanDisbursed payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    Instant now = Instant.now();
    for (Commitment commitment : commitmentRepository.findAllByLoanIdAndStatus(loanId, CommitmentStatus.ACTIVE)) {
      holdingRepository.save(
          Holding.builder()
              .id(UuidV7.generate())
              .lenderUserId(commitment.getLenderUserId())
              .loanId(loanId)
              .amountKobo(commitment.getAmountKobo())
              .status(HoldingStatus.ACTIVE)
              .createdAt(now)
              .build());
    }

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
