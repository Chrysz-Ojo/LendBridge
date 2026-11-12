package com.lendbridge.messaging.repository;

import com.lendbridge.messaging.model.Message;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

  /** UUIDv7 ids sort chronologically, so {@code id > afterId} is a correct cursor for both the
   * initial history page (afterId = a zero/min UUID sentinel) and the polling-fallback case
   * (afterId = the last message id the caller has already seen). */
  List<Message> findByConversationIdAndIdGreaterThanOrderByIdAsc(UUID conversationId, UUID afterId, Pageable pageable);
}
