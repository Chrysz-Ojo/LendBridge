package com.lendbridge.support.repository;

import com.lendbridge.support.model.Ticket;
import com.lendbridge.support.model.TicketStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

  List<Ticket> findByUserIdOrderByCreatedAtDesc(UUID userId);

  /** Backs the admin queue (PRD E11 "admin journey": work the support queue) — everything not yet
   * closed, oldest-updated first. */
  List<Ticket> findByStatusInOrderByUpdatedAtAsc(Collection<TicketStatus> statuses);
}
