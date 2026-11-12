package com.lendbridge.messaging.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.messaging.dto.MessageResponse;
import com.lendbridge.messaging.dto.SendMessageRequest;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.model.ConversationStatus;
import com.lendbridge.messaging.outbox.MessageSentPayload;
import com.lendbridge.messaging.realtime.ConversationEventPublisher;
import com.lendbridge.messaging.repository.ConversationRepository;
import com.lendbridge.messaging.repository.MessageRepository;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class MessageServiceImplTest {

  private MessageRepository messageRepository;
  private ConversationRepository conversationRepository;
  private OutboxEventWriter outboxEventWriter;
  private ConversationEventPublisher conversationEventPublisher;
  private MessageServiceImpl service;

  @BeforeEach
  void setUp() {
    messageRepository = mock(MessageRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    outboxEventWriter = mock(OutboxEventWriter.class);
    conversationEventPublisher = mock(ConversationEventPublisher.class);
    service =
        new MessageServiceImpl(messageRepository, conversationRepository, outboxEventWriter, conversationEventPublisher);
  }

  private Conversation conversation(UUID id, UUID borrowerUserId, UUID lenderUserId) {
    return Conversation.builder()
        .id(id)
        .loanId(UUID.randomUUID())
        .borrowerUserId(borrowerUserId)
        .lenderUserId(lenderUserId)
        .status(ConversationStatus.OPEN)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  @Test
  void send_senderIsBorrower_masksPersistsPublishesAndRecipientIsLender() {
    UUID conversationId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId))
        .thenReturn(Optional.of(conversation(conversationId, borrowerUserId, lenderUserId)));

    MessageResponse response =
        service.send(
            SendMessageRequest.builder().conversationId(conversationId).body("call 08012345678").build(),
            borrowerUserId);

    assertThat(response.getBody()).isEqualTo("call [redacted]");
    assertThat(response.getConversationId()).isEqualTo(conversationId);
    assertThat(response.getSenderUserId()).isEqualTo(borrowerUserId);

    verify(messageRepository).save(any());
    verify(conversationEventPublisher).publish(response);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventWriter).write(any(), any(), any(), payloadCaptor.capture());
    MessageSentPayload payload = (MessageSentPayload) payloadCaptor.getValue();
    assertThat(payload.getSenderUserId()).isEqualTo(borrowerUserId);
    assertThat(payload.getRecipientUserId()).isEqualTo(lenderUserId);
  }

  @Test
  void send_senderIsLender_recipientIsBorrower() {
    UUID conversationId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId))
        .thenReturn(Optional.of(conversation(conversationId, borrowerUserId, lenderUserId)));

    service.send(SendMessageRequest.builder().conversationId(conversationId).body("hello").build(), lenderUserId);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventWriter).write(any(), any(), any(), payloadCaptor.capture());
    assertThat(((MessageSentPayload) payloadCaptor.getValue()).getRecipientUserId()).isEqualTo(borrowerUserId);
  }

  @Test
  void send_senderNotAParticipant_throwsUnauthorizedAndNeverPersists() {
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId))
        .thenReturn(Optional.of(conversation(conversationId, UUID.randomUUID(), UUID.randomUUID())));

    assertThatThrownBy(
            () ->
                service.send(
                    SendMessageRequest.builder().conversationId(conversationId).body("hi").build(),
                    UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);

    verify(messageRepository, times(0)).save(any());
  }

  @Test
  void send_unknownConversation_throwsNotFound() {
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.send(
                    SendMessageRequest.builder().conversationId(conversationId).body("hi").build(),
                    UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void listSince_noCursor_usesZeroSentinelAndReturnsMappedResponses() {
    UUID conversationId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId))
        .thenReturn(Optional.of(conversation(conversationId, borrowerUserId, lenderUserId)));

    com.lendbridge.messaging.model.Message storedMessage =
        com.lendbridge.messaging.model.Message.builder()
            .id(UUID.randomUUID())
            .conversationId(conversationId)
            .senderUserId(borrowerUserId)
            .body("hi")
            .createdAt(Instant.now())
            .build();
    when(messageRepository.findByConversationIdAndIdGreaterThanOrderByIdAsc(
            org.mockito.ArgumentMatchers.eq(conversationId),
            org.mockito.ArgumentMatchers.eq(new UUID(0L, 0L)),
            any(Pageable.class)))
        .thenReturn(List.of(storedMessage));

    List<MessageResponse> result = service.listSince(conversationId, null, 50, borrowerUserId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getBody()).isEqualTo("hi");
  }

  @Test
  void listSince_callerNotAParticipant_throwsUnauthorized() {
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId))
        .thenReturn(Optional.of(conversation(conversationId, UUID.randomUUID(), UUID.randomUUID())));

    assertThatThrownBy(() -> service.listSince(conversationId, null, 50, UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);
  }
}
