package com.lendbridge.support.repository;

import com.lendbridge.support.model.TicketMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

  /** Ids are UUIDv7 (time-ordered), so ordering by id is chronological — same cursor idiom as
   * messaging-service's {@code MessageRepository}. */
  List<TicketMessage> findByTicketIdOrderByIdAsc(UUID ticketId);
}
