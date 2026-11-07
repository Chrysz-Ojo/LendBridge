package com.lendbridge.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.kyc.avro.KycRejected;
import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.repository.ProcessedEventRepository;
import com.lendbridge.notification.service.NotificationService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KycRejectedEventConsumerTest {

  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private NotificationService notificationService;

  private KycRejectedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new KycRejectedEventConsumer(processedEventRepository, notificationService);
  }

  @Test
  void onKycRejected_newEvent_dispatchesEmailWithReason() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID userId = UUID.randomUUID();
    KycRejected payload =
        KycRejected.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(userId.toString())
            .setReason("BVN mismatch")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, KycRejected> record = new ConsumerRecord<>("kyc.rejected", 0, 0L, userId.toString(), payload);

    consumer.onKycRejected(record, "event-1".getBytes());

    ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notificationService).dispatch(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    assertThat(captor.getValue().getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(captor.getValue().getType()).isEqualTo(NotificationType.KYC_REJECTED);
    assertThat(captor.getValue().getVariables()).containsEntry("reason", "BVN mismatch");
    verify(processedEventRepository).save(any());
  }

  @Test
  void onKycRejected_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    KycRejected payload =
        KycRejected.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(UUID.randomUUID().toString())
            .setReason("BVN mismatch")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, KycRejected> record = new ConsumerRecord<>("kyc.rejected", 0, 0L, "key", payload);

    consumer.onKycRejected(record, "event-2".getBytes());

    verify(notificationService, never()).dispatch(any());
    verify(processedEventRepository, never()).save(any());
  }
}
