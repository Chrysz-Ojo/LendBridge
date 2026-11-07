package com.lendbridge.notification.controller;

import com.lendbridge.notification.dto.ChannelPolicyRequest;
import com.lendbridge.notification.dto.ChannelPolicyResponse;
import com.lendbridge.notification.model.NotificationType;
import com.lendbridge.notification.service.ChannelPolicyService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform-wide channel cost-control: lets a super admin turn a channel off for an event type
 * (e.g. disable SMS for a high-volume reminder) without a code deploy. */
@RestController
@RequestMapping("/api/v1/notifications/admin/channel-policies")
@RequiredArgsConstructor
public class ChannelPolicyController {

  private final ChannelPolicyService channelPolicyService;

  @GetMapping
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<ApiResponse<List<ChannelPolicyResponse>>> listAll() {
    return ResponseEntity.ok(ApiResponse.success(channelPolicyService.listAll()));
  }

  @PutMapping("/{eventType}")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<ApiResponse<ChannelPolicyResponse>> upsert(
      @PathVariable NotificationType eventType, @Valid @RequestBody ChannelPolicyRequest request) {
    return ResponseEntity.ok(ApiResponse.success(channelPolicyService.upsert(eventType, request)));
  }
}
