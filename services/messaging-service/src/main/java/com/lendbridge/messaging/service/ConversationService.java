package com.lendbridge.messaging.service;

import com.lendbridge.messaging.dto.ConversationResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationService {

  /** Called by {@code InvestmentCommittedEventConsumer}: creates the conversation if a
   * {@code BorrowerLoanProjection} exists for the loan and one doesn't already exist for this
   * (loanId, lenderUserId) pair. No-op (logged) if the projection is missing — see ADR 0019. */
  void openOnCommitment(UUID loanId, UUID lenderUserId, Instant occurredAt);

  List<ConversationResponse> findMine(UUID callerUserId);

  ConversationResponse getOrThrow(UUID conversationId);

  /** Throws if {@code callerUserId} is neither the borrower nor the lender on this conversation. */
  void assertParticipant(UUID conversationId, UUID callerUserId);
}
