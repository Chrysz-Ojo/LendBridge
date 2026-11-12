package com.lendbridge.messaging.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.messaging.model.BorrowerLoanProjection;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.model.ConversationStatus;
import com.lendbridge.messaging.repository.BorrowerLoanProjectionRepository;
import com.lendbridge.messaging.repository.ConversationRepository;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConversationServiceImplTest {

  private ConversationRepository conversationRepository;
  private BorrowerLoanProjectionRepository borrowerLoanProjectionRepository;
  private ConversationServiceImpl service;

  @BeforeEach
  void setUp() {
    conversationRepository = mock(ConversationRepository.class);
    borrowerLoanProjectionRepository = mock(BorrowerLoanProjectionRepository.class);
    service = new ConversationServiceImpl(conversationRepository, borrowerLoanProjectionRepository);
  }

  @Test
  void openOnCommitment_projectionExistsAndNoExistingConversation_createsOpenConversation() {
    UUID loanId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();
    when(conversationRepository.findByLoanIdAndLenderUserId(loanId, lenderUserId)).thenReturn(Optional.empty());
    when(borrowerLoanProjectionRepository.findById(loanId))
        .thenReturn(
            Optional.of(
                BorrowerLoanProjection.builder()
                    .loanId(loanId)
                    .borrowerUserId(borrowerUserId)
                    .updatedAt(Instant.now())
                    .build()));

    service.openOnCommitment(loanId, lenderUserId, Instant.now());

    ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
    verify(conversationRepository).save(captor.capture());
    assertThat(captor.getValue().getLoanId()).isEqualTo(loanId);
    assertThat(captor.getValue().getLenderUserId()).isEqualTo(lenderUserId);
    assertThat(captor.getValue().getBorrowerUserId()).isEqualTo(borrowerUserId);
    assertThat(captor.getValue().getStatus()).isEqualTo(ConversationStatus.OPEN);
  }

  @Test
  void openOnCommitment_conversationAlreadyExists_isANoOp() {
    UUID loanId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    when(conversationRepository.findByLoanIdAndLenderUserId(loanId, lenderUserId))
        .thenReturn(Optional.of(Conversation.builder().id(UUID.randomUUID()).build()));

    service.openOnCommitment(loanId, lenderUserId, Instant.now());

    verify(conversationRepository, never()).save(any());
    verify(borrowerLoanProjectionRepository, never()).findById(any());
  }

  @Test
  void openOnCommitment_missingProjection_logsAndSkipsAcceptedEdgeCase() {
    UUID loanId = UUID.randomUUID();
    UUID lenderUserId = UUID.randomUUID();
    when(conversationRepository.findByLoanIdAndLenderUserId(loanId, lenderUserId)).thenReturn(Optional.empty());
    when(borrowerLoanProjectionRepository.findById(loanId)).thenReturn(Optional.empty());

    service.openOnCommitment(loanId, lenderUserId, Instant.now());

    verify(conversationRepository, never()).save(any());
  }

  @Test
  void assertParticipant_borrowerIsParticipant_doesNotThrow() {
    UUID conversationId = UUID.randomUUID();
    UUID borrowerUserId = UUID.randomUUID();
    Conversation conversation =
        Conversation.builder().id(conversationId).borrowerUserId(borrowerUserId).lenderUserId(UUID.randomUUID()).build();
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

    service.assertParticipant(conversationId, borrowerUserId);
  }

  @Test
  void assertParticipant_notAParticipant_throwsUnauthorized() {
    UUID conversationId = UUID.randomUUID();
    Conversation conversation =
        Conversation.builder()
            .id(conversationId)
            .borrowerUserId(UUID.randomUUID())
            .lenderUserId(UUID.randomUUID())
            .build();
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

    assertThatThrownBy(() -> service.assertParticipant(conversationId, UUID.randomUUID()))
        .isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void getOrThrow_unknownConversation_throwsNotFound() {
    UUID conversationId = UUID.randomUUID();
    when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getOrThrow(conversationId)).isInstanceOf(NotFoundException.class);
  }
}
