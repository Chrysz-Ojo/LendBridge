package com.lendbridge.support.service;

import com.lendbridge.support.dto.CreateTicketRequest;
import com.lendbridge.support.dto.ReplyTicketRequest;
import com.lendbridge.support.dto.TicketMessageResponse;
import com.lendbridge.support.dto.TicketResponse;
import com.lendbridge.support.dto.UpdateTicketStatusRequest;
import java.util.List;
import java.util.UUID;

public interface TicketService {

  TicketResponse create(CreateTicketRequest request, UUID userId);

  List<TicketResponse> findMine(UUID userId);

  TicketResponse getDetail(UUID ticketId, UUID callerUserId, boolean callerIsSupportStaff);

  List<TicketMessageResponse> listMessages(UUID ticketId, UUID callerUserId, boolean callerIsSupportStaff);

  TicketMessageResponse reply(
      UUID ticketId, ReplyTicketRequest request, UUID authorUserId, boolean authorIsSupportStaff);

  List<TicketResponse> findForAdminQueue();

  TicketResponse updateStatus(UUID ticketId, UpdateTicketStatusRequest request);
}
