package com.lendbridge.messaging.repository;

import com.lendbridge.messaging.model.Conversation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  Optional<Conversation> findByLoanIdAndLenderUserId(UUID loanId, UUID lenderUserId);

  List<Conversation> findByBorrowerUserIdOrLenderUserId(UUID borrowerUserId, UUID lenderUserId);
}
