package com.lendbridge.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.Contact;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.repository.ContactRepository;
import com.lendbridge.notification.repository.ProcessedEventRepository;
import com.lendbridge.notification.service.NotificationService;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventConsumerTest {

  @Mock private ContactRepository contactRepository;
  @Mock private ProcessedEventRepository processedEventRepository;
  @Mock private NotificationService notificationService;

  private UserRegisteredEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer = new UserRegisteredEventConsumer(contactRepository, processedEventRepository, notificationService);
  }

  @Test
  void onUserRegistered_newEvent_upsertsContactAndDispatchesWelcomeEmail() {
    when(processedEventRepository.existsById(any())).thenReturn(false);
    when(contactRepository.findById(any())).thenReturn(Optional.empty());
    UUID userId = UUID.randomUUID();
    String email = "ada@example.com";
    UserRegistered payload =
        UserRegistered.newBuilder()
            .setUserId(userId.toString())
            .setEmail(email)
            .setEmailVerified(true)
            .setPhoneVerified(true)
            .setRegisteredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, UserRegistered> record = new ConsumerRecord<>("user.registered", 0, 0L, userId.toString(), payload);

    consumer.onUserRegistered(record, "event-1".getBytes());

    ArgumentCaptor<Contact> contactCaptor = ArgumentCaptor.forClass(Contact.class);
    verify(contactRepository).save(contactCaptor.capture());
    assertThat(contactCaptor.getValue().getUserId()).isEqualTo(userId);
    assertThat(contactCaptor.getValue().getEmail()).isEqualTo(email);

    ArgumentCaptor<NotificationRequest> requestCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
    verify(notificationService).dispatch(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getUserId()).isEqualTo(userId);
    assertThat(requestCaptor.getValue().getChannel()).isEqualTo(Channel.EMAIL);
    assertThat(requestCaptor.getValue().getType()).isEqualTo(NotificationType.USER_REGISTERED);

    verify(processedEventRepository).save(any());
  }

  @Test
  void onUserRegistered_alreadyProcessedEvent_isSkipped() {
    when(processedEventRepository.existsById("event-2")).thenReturn(true);
    UserRegistered payload =
        UserRegistered.newBuilder()
            .setUserId(UUID.randomUUID().toString())
            .setEmail("ada@example.com")
            .setEmailVerified(true)
            .setPhoneVerified(true)
            .setRegisteredAtEpochMillis(System.currentTimeMillis())
            .build();
    ConsumerRecord<String, UserRegistered> record = new ConsumerRecord<>("user.registered", 0, 0L, "key", payload);

    consumer.onUserRegistered(record, "event-2".getBytes());

    verify(contactRepository, never()).save(any());
    verify(notificationService, never()).dispatch(any());
    verify(processedEventRepository, never()).save(any());
  }
}
