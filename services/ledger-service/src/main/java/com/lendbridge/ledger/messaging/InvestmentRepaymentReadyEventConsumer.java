package com.lendbridge.ledger.messaging;

import com.lendbridge.investment.avro.InvestmentRepaymentReady;
import com.lendbridge.investment.avro.RepaymentLeg;
import com.lendbridge.ledger.loansaga.LoanRepaymentPoster;
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

/** Same in-process-bean-call shape as {@link InvestmentDisbursementReadyEventConsumer} — see that
 * class's javadoc for why this method isn't itself {@code @Transactional}. */
@Component
@RequiredArgsConstructor
public class InvestmentRepaymentReadyEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(InvestmentRepaymentReadyEventConsumer.class);

  private final LoanRepaymentPoster poster;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "investment.repayment.ready", groupId = "ledger-service")
  public void onReady(
      ConsumerRecord<String, InvestmentRepaymentReady> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed investment.repayment.ready event {}", eventId);
      return;
    }

    InvestmentRepaymentReady payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    UUID instalmentId = UUID.fromString(payload.getInstalmentId().toString());
    try {
      poster.post(
          loanId,
          instalmentId,
          UUID.fromString(payload.getBorrowerLedgerAccountId().toString()),
          toLegs(payload.getLegs()),
          payload.getTotalKobo());
    } catch (Exception e) {
      log.error("Failed to post repayment journal entry for loan {} instalment {}", loanId, instalmentId, e);
      poster.recordFailure(loanId, instalmentId, e.getMessage());
    }

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }

  private static List<LoanRepaymentPoster.RepaymentLeg> toLegs(List<RepaymentLeg> avroLegs) {
    return avroLegs.stream()
        .map(
            l ->
                new LoanRepaymentPoster.RepaymentLeg(
                    UUID.fromString(l.getLenderLedgerAccountId().toString()), l.getAmountKobo()))
        .toList();
  }
}
