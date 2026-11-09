package com.lendbridge.notification.messaging;

import com.lendbridge.loan.avro.LoanListed;
import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.model.ProcessedEvent;
import com.lendbridge.notification.repository.ProcessedEventRepository;
import com.lendbridge.notification.service.NotificationService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Notifies the borrower that their loan request is live (PRD E7-S5). Idempotent against
 * redelivery via {@code processed_event}, same pattern as {@link UserRegisteredEventConsumer}. */
@Component
@RequiredArgsConstructor
public class LoanListedEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(LoanListedEventConsumer.class);

  private final ProcessedEventRepository processedEventRepository;
  private final NotificationService notificationService;

  @KafkaListener(topics = "loan.listed", groupId = "notification-service")
  @Transactional
  public void onLoanListed(
      ConsumerRecord<String, LoanListed> record, @Header(name = "ce_id", required = false) byte[] ceIdHeader) {
    String eventId = ceIdHeader != null ? new String(ceIdHeader) : record.topic() + "-" + record.offset();
    if (processedEventRepository.existsById(eventId)) {
      log.info("Skipping already-processed loan.listed event {}", eventId);
      return;
    }

    LoanListed payload = record.value();
    UUID borrowerUserId = UUID.fromString(payload.getBorrowerUserId().toString());

    notificationService.dispatch(
        NotificationRequest.builder()
            .userId(borrowerUserId)
            .channel(Channel.EMAIL)
            .type(NotificationType.LOAN_LISTED)
            .variables(
                Map.of(
                    "amountKobo", String.valueOf(payload.getAmountKobo()),
                    "interestRateBps", String.valueOf(payload.getInterestRateBps()),
                    "tenorMonths", String.valueOf(payload.getTenorMonths())))
            .build());

    processedEventRepository.save(ProcessedEvent.builder().eventId(eventId).processedAt(Instant.now()).build());
  }
}
