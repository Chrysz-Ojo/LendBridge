package com.lendbridge.notification.service.impl;

import com.lendbridge.notification.dto.NotificationRequest;
import com.lendbridge.notification.dto.NotificationResponse;
import com.lendbridge.notification.mapper.NotificationMapper;
import com.lendbridge.notification.model.Channel;
import com.lendbridge.notification.model.ChannelPolicy;
import com.lendbridge.notification.model.ChannelSettings;
import com.lendbridge.notification.model.Contact;
import com.lendbridge.notification.model.DeliveryAttempt;
import com.lendbridge.notification.model.EventPreference;
import com.lendbridge.notification.model.Notification;
import com.lendbridge.notification.model.NotificationStatus;
import com.lendbridge.notification.model.NotificationTemplate;
import com.lendbridge.notification.outbox.KafkaTopics;
import com.lendbridge.notification.outbox.NotificationSentPayload;
import com.lendbridge.notification.provider.EmailSender;
import com.lendbridge.notification.provider.SmsSender;
import com.lendbridge.notification.repository.ChannelPolicyRepository;
import com.lendbridge.notification.repository.ChannelSettingsRepository;
import com.lendbridge.notification.repository.ContactRepository;
import com.lendbridge.notification.repository.DeliveryAttemptRepository;
import com.lendbridge.notification.repository.EventPreferenceRepository;
import com.lendbridge.notification.repository.NotificationRepository;
import com.lendbridge.notification.repository.NotificationTemplateRepository;
import com.lendbridge.notification.service.NotificationService;
import com.lendbridge.notification.template.TemplateRenderer;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

  private final NotificationTemplateRepository templateRepository;
  private final NotificationRepository notificationRepository;
  private final ContactRepository contactRepository;
  private final ChannelSettingsRepository channelSettingsRepository;
  private final EventPreferenceRepository eventPreferenceRepository;
  private final ChannelPolicyRepository channelPolicyRepository;
  private final DeliveryAttemptRepository deliveryAttemptRepository;
  private final TemplateRenderer renderer;
  private final EmailSender emailSender;
  private final SmsSender smsSender;
  private final OutboxEventWriter outboxEventWriter;
  private final NotificationMapper notificationMapper;

  @Override
  @Transactional
  public Notification dispatch(NotificationRequest request) {
    Map<String, String> variables = request.getVariables() == null ? Map.of() : request.getVariables();
    NotificationTemplate template =
        templateRepository
            .findFirstByEventTypeAndChannelAndActiveTrueOrderByVersionDesc(
                request.getType().name(), request.getChannel())
            .orElse(null);

    String subject =
        request.getSubject() != null
            ? request.getSubject()
            : template != null ? renderer.render(template.getSubject(), variables) : null;
    String body =
        request.getBody() != null
            ? request.getBody()
            : template != null ? renderer.render(template.getBody(), variables) : null;

    if (body == null) {
      throw new IllegalStateException(
          "No body and no template for " + request.getType() + "/" + request.getChannel());
    }

    Notification notification =
        Notification.builder()
            .id(UuidV7.generate())
            .userId(request.getUserId())
            .eventType(request.getType().name())
            .channel(request.getChannel())
            .subject(subject)
            .body(body)
            .status(NotificationStatus.PENDING)
            .createdAt(Instant.now())
            .build();

    ChannelSettings settings =
        channelSettingsRepository.findById(request.getUserId()).orElseGet(() -> ChannelSettings.defaults(request.getUserId()));
    ChannelPolicy policy = channelPolicyRepository.findById(request.getType()).orElseGet(() -> ChannelPolicy.defaults(request.getType()));
    EventPreference preference =
        eventPreferenceRepository.findByUserIdAndEventType(request.getUserId(), request.getType().name()).orElse(null);

    if (shouldSkip(request, policy, preference, settings)) {
      notification.setStatus(NotificationStatus.SKIPPED);
    } else {
      attemptSend(request, notification);
    }

    notificationRepository.save(notification);

    if (notification.getStatus() == NotificationStatus.SENT) {
      outboxEventWriter.write(
          "Notification",
          notification.getId().toString(),
          KafkaTopics.NOTIFICATION_SENT,
          NotificationSentPayload.builder()
              .notificationId(notification.getId())
              .userId(notification.getUserId())
              .channel(notification.getChannel().name())
              .eventType(notification.getEventType())
              .status(notification.getStatus().name())
              .occurredAtEpochMillis(System.currentTimeMillis())
              .build());
    }

    return notification;
  }

  @Override
  @Transactional(readOnly = true)
  public List<NotificationResponse> listForUser(UUID userId, boolean unreadOnly) {
    List<Notification> notifications =
        unreadOnly
            ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId)
            : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    return notificationMapper.toResponseList(notifications);
  }

  @Override
  @Transactional
  public NotificationResponse markRead(UUID userId, UUID notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "No notification found for this user"));
    if (notification.getReadAt() == null) {
      notification.setReadAt(Instant.now());
      notificationRepository.save(notification);
    }
    return notificationMapper.toResponse(notification);
  }

  /**
   * Precedence, most authoritative first: (1) the admin {@link ChannelPolicy} for this event type
   * — a platform-wide, cost-driven kill switch that overrides everything below; (2) the blanket
   * marketing opt-in; (3) the user's own {@link EventPreference} for this event type, if they've
   * set one; (4) the user's blanket {@link ChannelSettings} channel toggle.
   */
  private boolean shouldSkip(NotificationRequest request, ChannelPolicy policy, EventPreference preference, ChannelSettings settings) {
    if (!request.getType().isTransactional() && !settings.isMarketingOptIn()) {
      return true;
    }
    return !isChannelEnabled(request.getChannel(), policy, preference, settings);
  }

  private boolean isChannelEnabled(Channel channel, ChannelPolicy policy, EventPreference preference, ChannelSettings settings) {
    if (!policyAllows(channel, policy)) {
      return false;
    }
    if (preference != null) {
      return preferenceAllows(channel, preference);
    }
    return settingsAllow(channel, settings);
  }

  private boolean policyAllows(Channel channel, ChannelPolicy policy) {
    return switch (channel) {
      case EMAIL -> policy.isEmailEnabled();
      case SMS -> policy.isSmsEnabled();
      case IN_APP -> policy.isInAppEnabled();
    };
  }

  private boolean preferenceAllows(Channel channel, EventPreference preference) {
    return switch (channel) {
      case EMAIL -> preference.isEmailEnabled();
      case SMS -> preference.isSmsEnabled();
      case IN_APP -> preference.isInAppEnabled();
    };
  }

  private boolean settingsAllow(Channel channel, ChannelSettings settings) {
    return switch (channel) {
      case EMAIL -> settings.isEmailEnabled();
      case SMS -> settings.isSmsEnabled();
      case IN_APP -> true;
    };
  }

  private void attemptSend(NotificationRequest request, Notification notification) {
    String error = null;
    try {
      switch (request.getChannel()) {
        case EMAIL -> emailSender.send(recipientEmail(request), notification.getSubject(), notification.getBody());
        case SMS -> smsSender.send(recipientPhone(request), notification.getBody());
        case IN_APP -> { /* stored only; shown in the in-app notification centre */ }
      }
      notification.setStatus(NotificationStatus.SENT);
    } catch (RuntimeException e) {
      log.warn(
          "Sending {} {} to user {} failed: {}",
          request.getChannel(), request.getType(), request.getUserId(), e.getMessage());
      notification.setStatus(NotificationStatus.FAILED);
      error = e.getMessage();
    }

    deliveryAttemptRepository.save(
        DeliveryAttempt.builder()
            .id(UuidV7.generate())
            .notificationId(notification.getId())
            .provider(request.getChannel().name())
            .status(notification.getStatus())
            .error(error)
            .attemptedAt(Instant.now())
            .build());
  }

  private String recipientEmail(NotificationRequest request) {
    if (request.getRecipient() != null) {
      return request.getRecipient();
    }
    return contactRepository.findById(request.getUserId()).map(Contact::getEmail).orElse(null);
  }

  private String recipientPhone(NotificationRequest request) {
    if (request.getRecipient() != null) {
      return request.getRecipient();
    }
    return contactRepository.findById(request.getUserId()).map(Contact::getPhone).orElse(null);
  }
}
