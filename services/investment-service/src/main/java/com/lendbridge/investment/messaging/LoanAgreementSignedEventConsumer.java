package com.lendbridge.investment.messaging;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.loan.avro.LoanAgreementSigned;
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
 * Funding saga Stage 3's investment-side leg: requests lender hold capture for every ACTIVE
 * commitment on the now-signed loan, and seeds the {@link DisbursementAssembler}'s join with the
 * origination fee. Atomic with the consumer's own idempotency bookkeeping — unlike the ledger
 * poster pattern, nothing here needs to survive a rollback of its own business action.
 */
@Component
@RequiredArgsConstructor
public class LoanAgreementSignedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanAgreementSignedEventConsumer.class);

  private final DisbursementAssembler disbursementAssembler;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "loan.agreement.signed", groupId = "investment-service")
  @Transactional
  public void onSigned(
      ConsumerRecord<String, LoanAgreementSigned> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.agreement.signed event {}", eventId);
      return;
    }

    LoanAgreementSigned payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    disbursementAssembler.onAgreementSigned(loanId, payload.getOriginationFeeKobo());

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
