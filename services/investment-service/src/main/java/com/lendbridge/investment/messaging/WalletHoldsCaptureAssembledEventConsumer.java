package com.lendbridge.investment.messaging;

import com.lendbridge.investment.fundingsaga.DisbursementAssembler;
import com.lendbridge.investment.model.ProcessedEvent;
import com.lendbridge.investment.repository.ProcessedEventRepository;
import com.lendbridge.wallet.avro.WalletHoldsCaptureAssembled;
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

/** Funding saga Stage 5's first join input — see {@link DisbursementAssembler}. */
@Component
@RequiredArgsConstructor
public class WalletHoldsCaptureAssembledEventConsumer {

  private static final Logger log =
      LoggerFactory.getLogger(WalletHoldsCaptureAssembledEventConsumer.class);

  private final DisbursementAssembler disbursementAssembler;
  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "wallet.holds.capture-assembled", groupId = "investment-service")
  @Transactional
  public void onCaptureAssembled(
      ConsumerRecord<String, WalletHoldsCaptureAssembled> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed wallet.holds.capture-assembled event {}", eventId);
      return;
    }

    WalletHoldsCaptureAssembled payload = record.value();
    UUID loanId = UUID.fromString(payload.getLoanId().toString());
    disbursementAssembler.onHoldsCaptureAssembled(
        loanId, payload.getSuccess(), payload.getFailureReason());

    processedEventRepository.save(
        ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
