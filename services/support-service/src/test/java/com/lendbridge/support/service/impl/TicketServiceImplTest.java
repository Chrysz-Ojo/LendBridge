package com.lendbridge.support.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.support.dto.CreateTicketRequest;
import com.lendbridge.support.dto.ReplyTicketRequest;
import com.lendbridge.support.dto.UpdateTicketStatusRequest;
import com.lendbridge.support.model.AuthorRole;
import com.lendbridge.support.model.Ticket;
import com.lendbridge.support.model.TicketCategory;
import com.lendbridge.support.model.TicketMessage;
import com.lendbridge.support.model.TicketStatus;
import com.lendbridge.support.repository.TicketMessageRepository;
import com.lendbridge.support.repository.TicketRepository;
import com.lendbridge.starter.exception.UnauthorizedException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TicketServiceImplTest {

  private TicketRepository ticketRepository;
  private TicketMessageRepository ticketMessageRepository;
  private OutboxEventWriter outboxEventWriter;
  private TicketServiceImpl service;

  @BeforeEach
  void setUp() {
    ticketRepository = mock(TicketRepository.class);
    ticketMessageRepository = mock(TicketMessageRepository.class);
    outboxEventWriter = mock(OutboxEventWriter.class);
    service = new TicketServiceImpl(ticketRepository, ticketMessageRepository, outboxEventWriter);
  }

  @Test
  void create_savesTicketAndFirstMessage() {
    UUID userId = UUID.randomUUID();
    CreateTicketRequest request = new CreateTicketRequest();
    request.setSubject("Can't withdraw");
    request.setCategory(TicketCategory.PAYMENT);
    request.setBody("My withdrawal has been pending for two days.");

    var response = service.create(request, userId);

    ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
    verify(ticketRepository).save(ticketCaptor.capture());
    assertThat(ticketCaptor.getValue().getStatus()).isEqualTo(TicketStatus.OPEN);
    assertThat(ticketCaptor.getValue().getUserId()).isEqualTo(userId);

    ArgumentCaptor<TicketMessage> messageCaptor = ArgumentCaptor.forClass(TicketMessage.class);
    verify(ticketMessageRepository).save(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getAuthorRole()).isEqualTo(AuthorRole.USER);
    assertThat(response.getStatus()).isEqualTo(TicketStatus.OPEN);
  }

  @Test
  void reply_asOwner_doesNotChangeStatusAndPublishesEvent() {
    UUID userId = UUID.randomUUID();
    Ticket ticket = openTicket(userId);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    service.reply(ticket.getId(), replyRequest("Any update?"), userId, false);

    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void reply_asStaff_movesOpenTicketToInProgressAndPublishesEvent() {
    UUID userId = UUID.randomUUID();
    Ticket ticket = openTicket(userId);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    service.reply(ticket.getId(), replyRequest("Looking into it now."), UUID.randomUUID(), true);

    assertThat(ticket.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    ArgumentCaptor<TicketMessage> messageCaptor = ArgumentCaptor.forClass(TicketMessage.class);
    verify(ticketMessageRepository).save(messageCaptor.capture());
    assertThat(messageCaptor.getValue().getAuthorRole()).isEqualTo(AuthorRole.STAFF);
    verify(outboxEventWriter, times(1)).write(any(), any(), any(), any());
  }

  @Test
  void reply_byNonOwnerNonStaff_throwsUnauthorized() {
    Ticket ticket = openTicket(UUID.randomUUID());
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    assertThatThrownBy(() -> service.reply(ticket.getId(), replyRequest("hi"), UUID.randomUUID(), false))
        .isInstanceOf(UnauthorizedException.class);
    verify(ticketMessageRepository, never()).save(any());
  }

  @Test
  void getDetail_byOwner_succeeds() {
    UUID userId = UUID.randomUUID();
    Ticket ticket = openTicket(userId);
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    var response = service.getDetail(ticket.getId(), userId, false);

    assertThat(response.getId()).isEqualTo(ticket.getId());
  }

  @Test
  void getDetail_byStaffNonOwner_succeeds() {
    Ticket ticket = openTicket(UUID.randomUUID());
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

    var response = service.getDetail(ticket.getId(), UUID.randomUUID(), true);

    assertThat(response.getId()).isEqualTo(ticket.getId());
  }

  @Test
  void updateStatus_resolvesTicketAndPublishesEvent() {
    Ticket ticket = openTicket(UUID.randomUUID());
    when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
    UpdateTicketStatusRequest request = new UpdateTicketStatusRequest();
    request.setStatus(TicketStatus.RESOLVED);

    var response = service.updateStatus(ticket.getId(), request);

    assertThat(response.getStatus()).isEqualTo(TicketStatus.RESOLVED);
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void findForAdminQueue_queriesOpenAndInProgressStatuses() {
    when(ticketRepository.findByStatusInOrderByUpdatedAtAsc(List.of(TicketStatus.OPEN, TicketStatus.IN_PROGRESS)))
        .thenReturn(List.of(openTicket(UUID.randomUUID())));

    var result = service.findForAdminQueue();

    assertThat(result).hasSize(1);
  }

  private static ReplyTicketRequest replyRequest(String body) {
    ReplyTicketRequest request = new ReplyTicketRequest();
    request.setBody(body);
    return request;
  }

  private static Ticket openTicket(UUID userId) {
    Instant now = Instant.now();
    return Ticket.builder()
        .id(UUID.randomUUID())
        .userId(userId)
        .subject("Subject")
        .category(TicketCategory.ACCOUNT)
        .status(TicketStatus.OPEN)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
