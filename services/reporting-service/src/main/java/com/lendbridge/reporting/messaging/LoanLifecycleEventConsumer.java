package com.lendbridge.reporting.messaging;

import com.lendbridge.loan.avro.LoanDelisted;
import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.loan.avro.LoanSubmitted;
import com.lendbridge.reporting.model.LoanPipelineFact;
import com.lendbridge.reporting.model.LoanPipelineStatus;
import com.lendbridge.reporting.model.ProcessedEvent;
import com.lendbridge.reporting.repository.LoanPipelineFactRepository;
import com.lendbridge.reporting.repository.ProcessedEventRepository;
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

/** Three listeners (one per topic), all writing the same {@link LoanPipelineFact} shape — one row
 * per lifecycle event, not just current state (see ADR 0022). {@code loan.delisted} carries no
 * amount/tenor/rate, so those columns are zeroed on a {@code DELISTED} row; the status itself is
 * still the useful signal for the funnel. */
@Component
@RequiredArgsConstructor
public class LoanLifecycleEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanLifecycleEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final LoanPipelineFactRepository loanPipelineFactRepository;

  @KafkaListener(topics = "loan.submitted", groupId = "reporting-service")
  @Transactional
  public void onLoanSubmitted(
      ConsumerRecord<String, LoanSubmitted> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.submitted event {}", eventId);
      return;
    }
    LoanSubmitted payload = record.value();
    save(
        eventId,
        payload.getLoanId().toString(),
        payload.getBorrowerUserId().toString(),
        LoanPipelineStatus.SUBMITTED,
        payload.getAmountKobo(),
        payload.getTenorMonths(),
        payload.getInterestRateBps(),
        payload.getOccurredAtEpochMillis());
  }

  @KafkaListener(topics = "loan.listed", groupId = "reporting-service")
  @Transactional
  public void onLoanListed(
      ConsumerRecord<String, LoanListed> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.listed event {}", eventId);
      return;
    }
    LoanListed payload = record.value();
    save(
        eventId,
        payload.getLoanId().toString(),
        payload.getBorrowerUserId().toString(),
        LoanPipelineStatus.LISTED,
        payload.getAmountKobo(),
        payload.getTenorMonths(),
        payload.getInterestRateBps(),
        payload.getOccurredAtEpochMillis());
  }

  /** {@code loan.delisted} carries only {@code loanId}/{@code occurredAtEpochMillis} — no
   * borrower/amount/tenor/rate — so this row's borrower id falls back to the all-zero UUID
   * sentinel and the money fields are zeroed. The {@code DELISTED} status itself is still the
   * useful funnel signal. */
  @KafkaListener(topics = "loan.delisted", groupId = "reporting-service")
  @Transactional
  public void onLoanDelisted(
      ConsumerRecord<String, LoanDelisted> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = idFor(record, ceIdHeader);
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.delisted event {}", eventId);
      return;
    }
    LoanDelisted payload = record.value();
    save(
        eventId,
        payload.getLoanId().toString(),
        new UUID(0L, 0L).toString(),
        LoanPipelineStatus.DELISTED,
        0L,
        0,
        0,
        payload.getOccurredAtEpochMillis());
  }

  private void save(
      String eventId,
      String loanId,
      String borrowerUserId,
      LoanPipelineStatus status,
      long amountKobo,
      int tenorMonths,
      int interestRateBps,
      long occurredAtEpochMillis) {
    loanPipelineFactRepository.save(
        LoanPipelineFact.builder()
            .id(UuidV7.generate())
            .loanId(UUID.fromString(loanId))
            .borrowerUserId(UUID.fromString(borrowerUserId))
            .status(status)
            .amountKobo(amountKobo)
            .tenorMonths(tenorMonths)
            .interestRateBps(interestRateBps)
            .occurredAt(Instant.ofEpochMilli(occurredAtEpochMillis))
            .build());
    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  private static String idFor(ConsumerRecord<String, ?> record, byte[] ceIdHeader) {
    String ceId = ceIdHeader != null ? new String(ceIdHeader) : null;
    return record.topic() + ":" + (ceId != null ? ceId : String.valueOf(record.offset()));
  }
}
