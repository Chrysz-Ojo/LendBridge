package com.lendbridge.support.service.impl;

import com.lendbridge.support.dto.CreateTicketRequest;
import com.lendbridge.support.dto.ReplyTicketRequest;
import com.lendbridge.support.dto.TicketMessageResponse;
import com.lendbridge.support.dto.TicketResponse;
import com.lendbridge.support.dto.UpdateTicketStatusRequest;
import com.lendbridge.support.model.AuthorRole;
import com.lendbridge.support.model.Ticket;
import com.lendbridge.support.model.TicketMessage;
import com.lendbridge.support.model.TicketStatus;
import com.lendbridge.support.outbox.KafkaTopics;
import com.lendbridge.support.outbox.TicketUpdatedPayload;
import com.lendbridge.support.repository.TicketMessageRepository;
import com.lendbridge.support.repository.TicketRepository;
import com.lendbridge.support.service.TicketService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketServiceImpl implements TicketService {

  private static final List<TicketStatus> ADMIN_QUEUE_STATUSES = List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS);

  private final TicketRepository ticketRepository;
  private final TicketMessageRepository ticketMessageRepository;
  private final OutboxEventWriter outboxEventWriter;

  public TicketServiceImpl(
      TicketRepository ticketRepository,
      TicketMessageRepository ticketMessageRepository,
      OutboxEventWriter outboxEventWriter) {
    this.ticketRepository = ticketRepository;
    this.ticketMessageRepository = ticketMessageRepository;
    this.outboxEventWriter = outboxEventWriter;
  }

  @Override
  @Transactional
  public TicketResponse create(CreateTicketRequest request, UUID userId) {
    Instant now = Instant.now();
    Ticket ticket =
        Ticket.builder()
            .id(UuidV7.generate())
            .userId(userId)
            .subject(request.getSubject())
            .category(request.getCategory())
            .categoryDetail(request.getCategoryDetail())
            .status(TicketStatus.OPEN)
            .attachmentDocumentId(request.getAttachmentDocumentId())
            .createdAt(now)
            .updatedAt(now)
            .build();
    ticketRepository.save(ticket);

    ticketMessageRepository.save(
        TicketMessage.builder()
            .id(UuidV7.generate())
            .ticketId(ticket.getId())
            .authorUserId(userId)
            .authorRole(AuthorRole.USER)
            .body(request.getBody())
            .createdAt(now)
            .build());

    return toResponse(ticket);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TicketResponse> findMine(UUID userId) {
    return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(TicketServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public TicketResponse getDetail(UUID ticketId, UUID callerUserId, boolean callerIsSupportStaff) {
    Ticket ticket = findOrThrow(ticketId);
    assertCanAccess(ticket, callerUserId, callerIsSupportStaff);
    return toResponse(ticket);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TicketMessageResponse> listMessages(UUID ticketId, UUID callerUserId, boolean callerIsSupportStaff) {
    Ticket ticket = findOrThrow(ticketId);
    assertCanAccess(ticket, callerUserId, callerIsSupportStaff);
    return ticketMessageRepository.findByTicketIdOrderByIdAsc(ticketId).stream()
        .map(TicketServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public TicketMessageResponse reply(
      UUID ticketId, ReplyTicketRequest request, UUID authorUserId, boolean authorIsSupportStaff) {
    Ticket ticket = findOrThrow(ticketId);
    assertCanAccess(ticket, authorUserId, authorIsSupportStaff);

    Instant now = Instant.now();
    AuthorRole authorRole = authorIsSupportStaff ? AuthorRole.STAFF : AuthorRole.USER;
    TicketMessage message =
        TicketMessage.builder()
            .id(UuidV7.generate())
            .ticketId(ticketId)
            .authorUserId(authorUserId)
            .authorRole(authorRole)
            .body(request.getBody())
            .createdAt(now)
            .build();
    ticketMessageRepository.save(message);

    if (authorIsSupportStaff && ticket.getStatus() == TicketStatus.OPEN) {
      ticket.setStatus(TicketStatus.IN_PROGRESS);
    }
    ticket.setUpdatedAt(now);
    ticketRepository.save(ticket);

    outboxEventWriter.write(
        "Ticket",
        ticket.getId().toString(),
        KafkaTopics.TICKET_UPDATED,
        TicketUpdatedPayload.builder()
            .ticketId(ticket.getId())
            .userId(ticket.getUserId())
            .status(ticket.getStatus())
            .lastMessageAuthorRole(authorRole)
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    return toResponse(message);
  }

  @Override
  @Transactional(readOnly = true)
  public List<TicketResponse> findForAdminQueue() {
    return ticketRepository.findByStatusInOrderByUpdatedAtAsc(ADMIN_QUEUE_STATUSES).stream()
        .map(TicketServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public TicketResponse updateStatus(UUID ticketId, UpdateTicketStatusRequest request) {
    Ticket ticket = findOrThrow(ticketId);
    Instant now = Instant.now();
    ticket.setStatus(request.getStatus());
    ticket.setUpdatedAt(now);
    ticketRepository.save(ticket);

    outboxEventWriter.write(
        "Ticket",
        ticket.getId().toString(),
        KafkaTopics.TICKET_UPDATED,
        TicketUpdatedPayload.builder()
            .ticketId(ticket.getId())
            .userId(ticket.getUserId())
            .status(ticket.getStatus())
            .lastMessageAuthorRole(AuthorRole.STAFF)
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    return toResponse(ticket);
  }

  private Ticket findOrThrow(UUID ticketId) {
    return ticketRepository
        .findById(ticketId)
        .orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "Ticket not found"));
  }

  /** Owner can always access their own ticket; support staff can access any ticket — mirrors
   * messaging-service's participant-or-staff authorization shape. */
  private static void assertCanAccess(Ticket ticket, UUID callerUserId, boolean callerIsSupportStaff) {
    if (!callerIsSupportStaff && !ticket.getUserId().equals(callerUserId)) {
      throw new UnauthorizedException("NOT_TICKET_OWNER", "You do not own this ticket");
    }
  }

  private static TicketResponse toResponse(Ticket ticket) {
    return TicketResponse.builder()
        .id(ticket.getId())
        .userId(ticket.getUserId())
        .subject(ticket.getSubject())
        .category(ticket.getCategory())
        .categoryDetail(ticket.getCategoryDetail())
        .status(ticket.getStatus())
        .attachmentDocumentId(ticket.getAttachmentDocumentId())
        .createdAt(ticket.getCreatedAt())
        .updatedAt(ticket.getUpdatedAt())
        .build();
  }

  private static TicketMessageResponse toResponse(TicketMessage message) {
    return TicketMessageResponse.builder()
        .id(message.getId())
        .ticketId(message.getTicketId())
        .authorUserId(message.getAuthorUserId())
        .authorRole(message.getAuthorRole())
        .body(message.getBody())
        .createdAt(message.getCreatedAt())
        .build();
  }
}
