package com.lendbridge.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.dto.NotificationResponse;
import com.lendbridge.notification.mapper.NotificationMapper;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.ChannelPolicy;
import com.lendbridge.notification.model.ChannelSettings;
import com.lendbridge.notification.model.Contact;
import com.lendbridge.notification.model.EventPreference;
import com.lendbridge.notification.model.Notification;
import com.lendbridge.notification.model.NotificationStatus;
import com.lendbridge.notification.model.NotificationTemplate;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.provider.EmailSender;
import com.lendbridge.notification.provider.SmsSender;
import com.lendbridge.notification.repository.ChannelPolicyRepository;
import com.lendbridge.notification.repository.ChannelSettingsRepository;
import com.lendbridge.notification.repository.ContactRepository;
import com.lendbridge.notification.repository.DeliveryAttemptRepository;
import com.lendbridge.notification.repository.EventPreferenceRepository;
import com.lendbridge.notification.repository.NotificationRepository;
import com.lendbridge.notification.repository.NotificationTemplateRepository;
import com.lendbridge.notification.template.TemplateRenderer;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

  @Mock private NotificationTemplateRepository templateRepository;
  @Mock private NotificationRepository notificationRepository;
  @Mock private ContactRepository contactRepository;
  @Mock private ChannelSettingsRepository channelSettingsRepository;
  @Mock private EventPreferenceRepository eventPreferenceRepository;
  @Mock private ChannelPolicyRepository channelPolicyRepository;
  @Mock private DeliveryAttemptRepository deliveryAttemptRepository;
  @Mock private TemplateRenderer renderer;
  @Mock private EmailSender emailSender;
  @Mock private SmsSender smsSender;
  @Mock private OutboxEventWriter outboxEventWriter;
  @Mock private NotificationMapper notificationMapper;

  private NotificationServiceImpl service;
  private UUID userId;

  @BeforeEach
  void setUp() {
    service =
        new NotificationServiceImpl(
            templateRepository,
            notificationRepository,
            contactRepository,
            channelSettingsRepository,
            eventPreferenceRepository,
            channelPolicyRepository,
            deliveryAttemptRepository,
            renderer,
            emailSender,
            smsSender,
            outboxEventWriter,
            notificationMapper);
    userId = UUID.randomUUID();
  }

  /** No admin channel-policy override and no per-user event-preference override for this type —
   * the defaults both resolve to "no restriction," matching pre-policy/preference behavior. */
  private void stubNoOverrides(NotificationType type) {
    when(channelPolicyRepository.findById(type)).thenReturn(Optional.empty());
    when(eventPreferenceRepository.findByUserIdAndEventType(userId, type.name())).thenReturn(Optional.empty());
  }

  @Test
  void dispatch_explicitBody_sendsEmailAndMarksSent() {
    stubNoOverrides(NotificationType.USER_REGISTERED);
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.empty());
    when(contactRepository.findById(userId)).thenReturn(Optional.of(Contact.builder().userId(userId).email("ada@example.com").build()));

    NotificationRequest request =
        NotificationRequest.builder()
            .userId(userId)
            .channel(Channel.EMAIL)
            .type(NotificationType.USER_REGISTERED)
            .subject("Welcome")
            .body("Hello there")
            .build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    verify(emailSender).send("ada@example.com", "Welcome", "Hello there");
    verify(notificationRepository).save(result);
    verify(deliveryAttemptRepository).save(any());
    verify(outboxEventWriter).write(eq("Notification"), anyString(), eq("notification.sent"), any());
  }

  @Test
  void dispatch_noExplicitBody_rendersFromTemplate() {
    stubNoOverrides(NotificationType.USER_REGISTERED);
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.empty());
    when(contactRepository.findById(userId)).thenReturn(Optional.of(Contact.builder().userId(userId).email("ada@example.com").build()));
    NotificationTemplate template = NotificationTemplate.builder().subject("Subj {name}").body("Body {name}").build();
    when(templateRepository.findFirstByEventTypeAndChannelAndActiveTrueOrderByVersionDesc("USER_REGISTERED", Channel.EMAIL))
        .thenReturn(Optional.of(template));
    when(renderer.render("Subj {name}", Map.of("name", "Ada"))).thenReturn("Subj Ada");
    when(renderer.render("Body {name}", Map.of("name", "Ada"))).thenReturn("Body Ada");

    NotificationRequest request =
        NotificationRequest.builder()
            .userId(userId)
            .channel(Channel.EMAIL)
            .type(NotificationType.USER_REGISTERED)
            .variables(Map.of("name", "Ada"))
            .build();

    Notification result = service.dispatch(request);

    assertThat(result.getSubject()).isEqualTo("Subj Ada");
    assertThat(result.getBody()).isEqualTo("Body Ada");
    verify(emailSender).send("ada@example.com", "Subj Ada", "Body Ada");
  }

  @Test
  void dispatch_noBodyAndNoTemplate_throws() {
    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.USER_REGISTERED).build();

    assertThatThrownBy(() -> service.dispatch(request)).isInstanceOf(IllegalStateException.class);
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void dispatch_smsChannel_sendsViaSmsSenderUsingBodyAsMessage() {
    stubNoOverrides(NotificationType.OTP);
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.empty());
    when(contactRepository.findById(userId)).thenReturn(Optional.of(Contact.builder().userId(userId).phone("08011112222").build()));

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.SMS).type(NotificationType.OTP).body("Your OTP is 1234").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    verify(smsSender).send("08011112222", "Your OTP is 1234");
  }

  @Test
  void dispatch_inAppChannel_storesWithoutCallingAnySender() {
    stubNoOverrides(NotificationType.LOAN_LISTED);
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.empty());

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.IN_APP).type(NotificationType.LOAN_LISTED).body("Your loan is live").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    verify(emailSender, never()).send(any(), any(), any());
    verify(smsSender, never()).send(any(), any());
  }

  @Test
  void dispatch_providerThrows_marksFailedRecordsAttemptAndSkipsOutbox() {
    stubNoOverrides(NotificationType.USER_REGISTERED);
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.empty());
    when(contactRepository.findById(userId)).thenReturn(Optional.of(Contact.builder().userId(userId).email("ada@example.com").build()));
    doThrow(new RuntimeException("smtp down")).when(emailSender).send(any(), any(), any());

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.USER_REGISTERED).subject("s").body("b").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
    verify(deliveryAttemptRepository).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void dispatch_marketingTypeWithoutOptIn_isSkipped() {
    stubNoOverrides(NotificationType.MARKETING);
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.of(ChannelSettings.defaults(userId)));

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.MARKETING).subject("s").body("b").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    verify(emailSender, never()).send(any(), any(), any());
    verify(deliveryAttemptRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void dispatch_transactionalType_sendsEvenWithoutMarketingOptIn() {
    stubNoOverrides(NotificationType.USER_REGISTERED);
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.of(ChannelSettings.defaults(userId)));
    when(contactRepository.findById(userId)).thenReturn(Optional.of(Contact.builder().userId(userId).email("ada@example.com").build()));

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.USER_REGISTERED).subject("s").body("b").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
  }

  @Test
  void dispatch_emailChannelDisabled_isSkipped() {
    stubNoOverrides(NotificationType.USER_REGISTERED);
    ChannelSettings settings = ChannelSettings.builder().userId(userId).emailEnabled(false).smsEnabled(true).marketingOptIn(false).build();
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.of(settings));

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.USER_REGISTERED).subject("s").body("b").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    verify(emailSender, never()).send(any(), any(), any());
  }

  @Test
  void dispatch_channelPolicyDisablesChannel_isSkippedEvenWhenUserSettingsAllowIt() {
    when(channelPolicyRepository.findById(NotificationType.USER_REGISTERED))
        .thenReturn(Optional.of(ChannelPolicy.builder().eventType(NotificationType.USER_REGISTERED).emailEnabled(false).smsEnabled(true).inAppEnabled(true).build()));
    when(eventPreferenceRepository.findByUserIdAndEventType(userId, "USER_REGISTERED")).thenReturn(Optional.empty());
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.of(ChannelSettings.defaults(userId)));

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.USER_REGISTERED).subject("s").body("b").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    verify(emailSender, never()).send(any(), any(), any());
  }

  @Test
  void dispatch_eventPreferenceDisablesChannel_isSkippedEvenWhenBlanketSettingsAllow() {
    stubNoOverridesExceptPreference(
        NotificationType.LOAN_LISTED,
        EventPreference.builder().userId(userId).eventType("LOAN_LISTED").emailEnabled(false).smsEnabled(true).inAppEnabled(true).build());
    when(channelSettingsRepository.findById(userId)).thenReturn(Optional.of(ChannelSettings.defaults(userId)));

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.LOAN_LISTED).subject("s").body("b").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    verify(emailSender, never()).send(any(), any(), any());
  }

  @Test
  void dispatch_eventPreferenceEnablesChannel_sendsEvenWhenBlanketSettingsDisallow() {
    stubNoOverridesExceptPreference(
        NotificationType.LOAN_LISTED,
        EventPreference.builder().userId(userId).eventType("LOAN_LISTED").emailEnabled(true).smsEnabled(true).inAppEnabled(true).build());
    when(channelSettingsRepository.findById(userId))
        .thenReturn(Optional.of(ChannelSettings.builder().userId(userId).emailEnabled(false).smsEnabled(false).marketingOptIn(false).build()));
    when(contactRepository.findById(userId)).thenReturn(Optional.of(Contact.builder().userId(userId).email("ada@example.com").build()));

    NotificationRequest request =
        NotificationRequest.builder().userId(userId).channel(Channel.EMAIL).type(NotificationType.LOAN_LISTED).subject("s").body("b").build();

    Notification result = service.dispatch(request);

    assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
    verify(emailSender).send("ada@example.com", "s", "b");
  }

  private void stubNoOverridesExceptPreference(NotificationType type, EventPreference preference) {
    when(channelPolicyRepository.findById(type)).thenReturn(Optional.empty());
    when(eventPreferenceRepository.findByUserIdAndEventType(userId, type.name())).thenReturn(Optional.of(preference));
  }

  @Test
  void listForUser_unreadOnlyFalse_returnsAllNotificationsMapped() {
    Notification notification = Notification.builder().id(UUID.randomUUID()).userId(userId).build();
    NotificationResponse response = NotificationResponse.builder().id(notification.getId()).build();
    when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));
    when(notificationMapper.toResponseList(List.of(notification))).thenReturn(List.of(response));

    List<NotificationResponse> result = service.listForUser(userId, false);

    assertThat(result).containsExactly(response);
  }

  @Test
  void listForUser_unreadOnlyTrue_queriesUnreadNotifications() {
    when(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId)).thenReturn(List.of());
    when(notificationMapper.toResponseList(List.of())).thenReturn(List.of());

    service.listForUser(userId, true);

    verify(notificationRepository).findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId);
    verify(notificationRepository, never()).findByUserIdOrderByCreatedAtDesc(any());
  }

  @Test
  void markRead_unreadNotification_setsReadAtAndSaves() {
    UUID notificationId = UUID.randomUUID();
    Notification notification = Notification.builder().id(notificationId).userId(userId).build();
    when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));
    when(notificationMapper.toResponse(notification)).thenReturn(NotificationResponse.builder().id(notificationId).build());

    service.markRead(userId, notificationId);

    assertThat(notification.getReadAt()).isNotNull();
    verify(notificationRepository).save(notification);
  }

  @Test
  void markRead_alreadyReadNotification_doesNotResaveOrOverwriteReadAt() {
    UUID notificationId = UUID.randomUUID();
    Instant originalReadAt = Instant.now().minusSeconds(60);
    Notification notification = Notification.builder().id(notificationId).userId(userId).readAt(originalReadAt).build();
    when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.of(notification));
    when(notificationMapper.toResponse(notification)).thenReturn(NotificationResponse.builder().id(notificationId).build());

    service.markRead(userId, notificationId);

    assertThat(notification.getReadAt()).isEqualTo(originalReadAt);
    verify(notificationRepository, never()).save(any());
  }

  @Test
  void markRead_notFoundOrNotOwnedByUser_throwsNotFound() {
    UUID notificationId = UUID.randomUUID();
    when(notificationRepository.findByIdAndUserId(notificationId, userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.markRead(userId, notificationId)).isInstanceOf(NotFoundException.class);
  }
}
