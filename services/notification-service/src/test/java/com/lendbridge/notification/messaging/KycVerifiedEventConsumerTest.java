package com.lendbridge.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.kyc.avro.KycVerified;
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
class KycVerifiedEventConsumerTest {

  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private NotificationService notificationService;

  private KycVerifiedEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new KycVerifiedEventConsumer(processedEventRepository, notificationService);
  }

  @Test
  void onKycVerified_newEvent_dispatchesEmailWithTrustTier() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    UUID userId = UUID.randomUUID();
    KycVerified payload =
        KycVerified.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(userId.toString())
            .setTrustTier("TIER_1")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, KycVerified> record = new ConsumerRecord<>("kyc.verified", 0, 0L, userId.toString(), payload);

    consumer.onKycVerified(record, "event-1".getBytes());

    ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notificationService).dispatch(captor.capture());
    assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    assertThat(captor.getValue().getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(captor.getValue().getType()).isEqualTo(NotificationType.KYC_VERIFIED);
    assertThat(captor.getValue().getVariables()).containsEntry("trustTier", "TIER_1");
    verify(processedEventRepository).save(any());
  }

  @Test
  void onKycVerified_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    KycVerified payload =
        KycVerified.newBuilder()
            .setVerificationCaseId(UUID.randomUUID().toString())
            .setUserId(UUID.randomUUID().toString())
            .setTrustTier("TIER_1")
            .setOccurredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, KycVerified> record = new ConsumerRecord<>("kyc.verified", 0, 0L, "key", payload);

    consumer.onKycVerified(record, "event-2".getBytes());

    verify(notificationService, never()).dispatch(any());
    verify(processedEventRepository, never()).save(any());
  }
}
