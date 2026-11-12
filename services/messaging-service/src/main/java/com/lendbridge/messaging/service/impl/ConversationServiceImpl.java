package com.lendbridge.messaging.service.impl;

import com.lendbridge.messaging.dto.ConversationResponse;
import com.lendbridge.messaging.model.BorrowerLoanProjection;
import com.lendbridge.messaging.model.Conversation;
import com.lendbridge.messaging.model.ConversationStatus;
import com.lendbridge.messaging.repository.BorrowerLoanProjectionRepository;
import com.lendbridge.messaging.repository.ConversationRepository;
import com.lendbridge.messaging.service.ConversationService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationServiceImpl implements ConversationService {

  private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

  private final ConversationRepository conversationRepository;
  private final BorrowerLoanProjectionRepository borrowerLoanProjectionRepository;

  public ConversationServiceImpl(
      ConversationRepository conversationRepository,
      BorrowerLoanProjectionRepository borrowerLoanProjectionRepository) {
    this.conversationRepository = conversationRepository;
    this.borrowerLoanProjectionRepository = borrowerLoanProjectionRepository;
  }

  @Override
  @Transactional
  public void openOnCommitment(UUID loanId, UUID lenderUserId, Instant occurredAt) {
    if (conversationRepository.findByLoanIdAndLenderUserId(loanId, lenderUserId).isPresent()) {
      return;
    }

    Optional<BorrowerLoanProjection> projection = borrowerLoanProjectionRepository.findById(loanId);
    if (projection.isEmpty()) {
      log.warn(
          "No BorrowerLoanProjection for loan {} when investment.committed arrived for lender {} — "
              + "likely loan.listed consumer lag; skipping conversation creation (accepted edge case, see ADR 0019)",
          loanId,
          lenderUserId);
      return;
    }

    Instant now = Instant.now();
    conversationRepository.save(
        Conversation.builder()
            .id(UuidV7.generate())
            .loanId(loanId)
            .borrowerUserId(projection.get().getBorrowerUserId())
            .lenderUserId(lenderUserId)
            .status(ConversationStatus.OPEN)
            .createdAt(now)
            .updatedAt(now)
            .build());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConversationResponse> findMine(UUID callerUserId) {
    return conversationRepository.findByBorrowerUserIdOrLenderUserId(callerUserId, callerUserId).stream()
        .map(ConversationServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public ConversationResponse getOrThrow(UUID conversationId) {
    return toResponse(findOrThrow(conversationId));
  }

  @Override
  @Transactional(readOnly = true)
  public void assertParticipant(UUID conversationId, UUID callerUserId) {
    Conversation conversation = findOrThrow(conversationId);
    if (!conversation.getBorrowerUserId().equals(callerUserId) && !conversation.getLenderUserId().equals(callerUserId)) {
      throw new UnauthorizedException("NOT_A_PARTICIPANT", "You are not a participant in this conversation");
    }
  }

  private Conversation findOrThrow(UUID conversationId) {
    return conversationRepository
        .findById(conversationId)
        .orElseThrow(() -> new NotFoundException("CONVERSATION_NOT_FOUND", "Conversation not found"));
  }

  private static ConversationResponse toResponse(Conversation conversation) {
    return ConversationResponse.builder()
        .id(conversation.getId())
        .loanId(conversation.getLoanId())
        .borrowerUserId(conversation.getBorrowerUserId())
        .lenderUserId(conversation.getLenderUserId())
        .status(conversation.getStatus())
        .createdAt(conversation.getCreatedAt())
        .build();
  }
}
