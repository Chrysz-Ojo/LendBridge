package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.AdminTicketResponse;
import com.lendbridge.adminbff.dto.UpdateTicketStatusRequest;
import com.lendbridge.adminbff.supportticket.SupportTicketGateway;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/support/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPPORT')")
public class SupportTicketController {

  private final SupportTicketGateway supportTicketGateway;

  @GetMapping
  public ResponseEntity<ApiResponse<List<AdminTicketResponse>>> findForAdminQueue() {
    return ResponseEntity.ok(ApiResponse.success(supportTicketGateway.findForAdminQueue()));
  }

  @PatchMapping("/{ticketId}/status")
  public ResponseEntity<ApiResponse<AdminTicketResponse>> updateStatus(
      @PathVariable UUID ticketId, @Valid @RequestBody UpdateTicketStatusRequest request) {
    return ResponseEntity.ok(ApiResponse.success(supportTicketGateway.updateStatus(ticketId, request)));
  }
}
