package com.lendbridge.messaging.service.impl;

import com.lendbridge.messaging.dto.MessageResponse;
import com.lendbridge.messaging.dto.SendMessageRequest;
import com.lendbridge.messaging.masking.ContactInfoMasker;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.model.Message;
import com.lendbridge.messaging.outbox.KafkaTopics;
import com.lendbridge.messaging.outbox.MessageSentPayload;
import com.lendbridge.messaging.realtime.ConversationEventPublisher;
import com.lendbridge.messaging.repository.ConversationRepository;
import com.lendbridge.messaging.repository.MessageRepository;
import com.lendbridge.messaging.service.MessageService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageServiceImpl implements MessageService {

  /** Sentinel used as the cursor when the caller has no prior message id — UUIDv7's all-zero
   * value sorts before every real id, so {@code id > ZERO_CURSOR} returns from the beginning. */
  private static final UUID ZERO_CURSOR = new UUID(0L, 0L);

  private final MessageRepository messageRepository;
  private final ConversationRepository conversationRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final ConversationEventPublisher conversationEventPublisher;

  public MessageServiceImpl(
      MessageRepository messageRepository,
      ConversationRepository conversationRepository,
      OutboxEventWriter outboxEventWriter,
      ConversationEventPublisher conversationEventPublisher) {
    this.messageRepository = messageRepository;
    this.conversationRepository = conversationRepository;
    this.outboxEventWriter = outboxEventWriter;
    this.conversationEventPublisher = conversationEventPublisher;
  }

  @Override
  @Transactional
  public MessageResponse send(SendMessageRequest request, UUID senderUserId) {
    Conversation conversation = findConversationOrThrow(request.getConversationId());
    assertParticipant(conversation, senderUserId);

    Instant now = Instant.now();
    Message message =
        Message.builder()
            .id(UuidV7.generate())
            .conversationId(conversation.getId())
            .senderUserId(senderUserId)
            .body(ContactInfoMasker.mask(request.getBody()))
            .createdAt(now)
            .build();
    messageRepository.save(message);

    UUID recipientUserId =
        conversation.getBorrowerUserId().equals(senderUserId)
            ? conversation.getLenderUserId()
            : conversation.getBorrowerUserId();

    outboxEventWriter.write(
        "Message",
        message.getId().toString(),
        KafkaTopics.MESSAGE_SENT,
        MessageSentPayload.builder()
            .conversationId(conversation.getId())
            .messageId(message.getId())
            .senderUserId(senderUserId)
            .recipientUserId(recipientUserId)
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    MessageResponse response = toResponse(message);
    conversationEventPublisher.publish(response);
    return response;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MessageResponse> listSince(UUID conversationId, UUID afterId, int limit, UUID callerUserId) {
    Conversation conversation = findConversationOrThrow(conversationId);
    assertParticipant(conversation, callerUserId);

    UUID cursor = afterId != null ? afterId : ZERO_CURSOR;
    return messageRepository
        .findByConversationIdAndIdGreaterThanOrderByIdAsc(conversationId, cursor, PageRequest.of(0, limit))
        .stream()
        .map(MessageServiceImpl::toResponse)
        .toList();
  }

  private Conversation findConversationOrThrow(UUID conversationId) {
    return conversationRepository
        .findById(conversationId)
        .orElseThrow(() -> new NotFoundException("CONVERSATION_NOT_FOUND", "Conversation not found"));
  }

  private static void assertParticipant(Conversation conversation, UUID callerUserId) {
    if (!conversation.getBorrowerUserId().equals(callerUserId) && !conversation.getLenderUserId().equals(callerUserId)) {
      throw new UnauthorizedException("NOT_A_PARTICIPANT", "You are not a participant in this conversation");
    }
  }

  private static MessageResponse toResponse(Message message) {
    return MessageResponse.builder()
        .id(message.getId())
        .conversationId(message.getConversationId())
        .senderUserId(message.getSenderUserId())
        .body(message.getBody())
        .createdAt(message.getCreatedAt())
        .build();
  }
}
