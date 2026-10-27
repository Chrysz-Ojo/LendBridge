package com.lendbridge.ledger.messaging;

import com.lendbridge.ledger.model.ProcessedEvent;
import com.lendbridge.ledger.repository.ProcessedEventRepository;
import com.lendbridge.payment.avro.PaymentFailed;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Nothing was ever credited/debited to reverse on a failed payment, so this never posts a
 * journal entry — it only records {@code processed_event}, kept as a real consumer (not just
 * ignored entirely) so a future reconciliation/audit need has a record that ledger-service saw
 * the event. */
@Component
@RequiredArgsConstructor
public class PaymentFailedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentFailedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;

  @KafkaListener(topics = "payment.failed", groupId = "ledger-service")
  @Transactional
  public void onPaymentFailed(
      ConsumerRecord<String, PaymentFailed> record,
      @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed payment.failed event {}", eventId);
      return;
    }
    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
