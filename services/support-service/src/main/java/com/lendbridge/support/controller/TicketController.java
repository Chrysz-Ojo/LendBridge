package com.lendbridge.support.controller;

import com.lendbridge.support.dto.CreateTicketRequest;
import com.lendbridge.support.dto.ReplyTicketRequest;
import com.lendbridge.support.dto.TicketMessageResponse;
import com.lendbridge.support.dto.TicketResponse;
import com.lendbridge.support.dto.UpdateTicketStatusRequest;
import com.lendbridge.support.security.CurrentUser;
import com.lendbridge.support.service.TicketService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** PRD E9-S2: in-app tickets with threaded replies. Owner-or-staff access is checked inline
 * ({@link CurrentUser#isSupportStaff(Authentication)}) rather than split into separate user/admin
 * routes, since both sides genuinely share the same thread; the admin-queue listing and status
 * transition are staff-only via {@code @PreAuthorize}, same {@code SUPPORT}-role precedent as
 * messaging-service's abuse-report queue. */
@RestController
@RequiredArgsConstructor
public class TicketController {

  private final TicketService ticketService;

  @PostMapping("/api/v1/support/tickets")
  public ResponseEntity<ApiResponse<TicketResponse>> create(
      @Valid @RequestBody CreateTicketRequest request, Authentication authentication) {
    UUID userId = CurrentUser.idFrom(authentication);
    return ResponseEntity.ok(ApiResponse.success(ticketService.create(request, userId)));
  }

  @GetMapping("/api/v1/support/tickets")
  public ResponseEntity<ApiResponse<List<TicketResponse>>> findMine(Authentication authentication) {
    UUID userId = CurrentUser.idFrom(authentication);
    return ResponseEntity.ok(ApiResponse.success(ticketService.findMine(userId)));
  }

  @GetMapping("/api/v1/support/tickets/{ticketId}")
  public ResponseEntity<ApiResponse<TicketResponse>> getDetail(
      @PathVariable UUID ticketId, Authentication authentication) {
    UUID callerUserId = CurrentUser.idFrom(authentication);
    boolean isSupportStaff = CurrentUser.isSupportStaff(authentication);
    return ResponseEntity.ok(ApiResponse.success(ticketService.getDetail(ticketId, callerUserId, isSupportStaff)));
  }

  @GetMapping("/api/v1/support/tickets/{ticketId}/messages")
  public ResponseEntity<ApiResponse<List<TicketMessageResponse>>> listMessages(
      @PathVariable UUID ticketId, Authentication authentication) {
    UUID callerUserId = CurrentUser.idFrom(authentication);
    boolean isSupportStaff = CurrentUser.isSupportStaff(authentication);
    return ResponseEntity.ok(
        ApiResponse.success(ticketService.listMessages(ticketId, callerUserId, isSupportStaff)));
  }

  @PostMapping("/api/v1/support/tickets/{ticketId}/messages")
  public ResponseEntity<ApiResponse<TicketMessageResponse>> reply(
      @PathVariable UUID ticketId, @Valid @RequestBody ReplyTicketRequest request, Authentication authentication) {
    UUID authorUserId = CurrentUser.idFrom(authentication);
    boolean isSupportStaff = CurrentUser.isSupportStaff(authentication);
    return ResponseEntity.ok(
        ApiResponse.success(ticketService.reply(ticketId, request, authorUserId, isSupportStaff)));
  }

  @GetMapping("/api/v1/support/admin/tickets")
  @PreAuthorize("hasRole('SUPPORT')")
  public ResponseEntity<ApiResponse<List<TicketResponse>>> findForAdminQueue() {
    return ResponseEntity.ok(ApiResponse.success(ticketService.findForAdminQueue()));
  }

  @PatchMapping("/api/v1/support/admin/tickets/{ticketId}/status")
  @PreAuthorize("hasRole('SUPPORT')")
  public ResponseEntity<ApiResponse<TicketResponse>> updateStatus(
      @PathVariable UUID ticketId, @Valid @RequestBody UpdateTicketStatusRequest request) {
    return ResponseEntity.ok(ApiResponse.success(ticketService.updateStatus(ticketId, request)));
  }
}
