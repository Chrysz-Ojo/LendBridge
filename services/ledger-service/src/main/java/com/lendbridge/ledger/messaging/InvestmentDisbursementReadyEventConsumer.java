package com.lendbridge.ledger.messaging;

import com.lendbridge.investment.avro.DisbursementLeg;
import com.lendbridge.investment.avro.InvestmentDisbursementReady;
import com.lendbridge.ledger.loansaga.LoanDisbursementPoster;
import com.lendbridge.ledger.model.ProcessedEvent;
import com.lendbridge.ledger.repository.ProcessedEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Posts both disbursement journal entries via {@link LoanDisbursementPoster} — a plain in-process
 * bean call, no HTTP, same reasoning as {@code PaymentSucceededEventConsumer} (ADR 0013). Not
 * itself {@code @Transactional}: {@link LoanDisbursementPoster#post} and
 * {@link LoanDisbursementPoster#recordFailure} each need their own transaction boundary (one that
 * rolls back on failure, one that commits the failure record regardless), so this method must call
 * them from outside any transaction of its own.
 */
@Component
@RequiredArgsConstructor
public class InvestmentDisbursementReadyEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentDisbursementReadyEventConsumer.class);

  private final LoanDisbursementPoster poster;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.disbursement.ready", groupId = "ledger-service")
  public void onReady(
      ConsumerRecord<String, InvestmentDisbursementReady> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.disbursement.ready event {}", eventId);
      return;
    }

    InvestmentDisbursementReady payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    try {
      poster.post(
          loanId,
          toLegs(payload.getLegs()),
          payload.getTotalKobo(),
          UUID.fromString(payload.getBorrowerLedgerAccountId().toString()),
          payload.getOriginationFeeKobo());
    } catch (Exception e) {
      log.error("Failed to post disbursement journal entries for loan {}", loanId, e);
      poster.recordFailure(loanId, e.getMessage());
    }

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  private static List<LoanDisbursementPoster.DisbursementLeg> toLegs(List<DisbursementLeg> avroLegs) {
    return avroLegs.stream()
        .map(
            l ->
                new LoanDisbursementPoster.DisbursementLeg(
                    UUID.fromString(l.getLenderLedgerAccountId().toString()), l.getAmountKobo()))
        .toList();
  }
}
