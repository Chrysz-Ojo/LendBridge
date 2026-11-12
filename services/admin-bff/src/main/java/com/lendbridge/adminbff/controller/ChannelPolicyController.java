package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.ChannelPolicyResponse;
import com.lendbridge.adminbff.notification.ChannelPolicyGateway;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only passthrough to notification-service's channel policies. Updating one no longer has a
 * direct endpoint here — it goes through {@code ApprovalController} with
 * {@code actionType=CHANNEL_POLICY_UPDATE}, since a policy change affects every user on the
 * platform and is worth a second {@code SUPER_ADMIN}'s sign-off (see ADR 0023). */
@RestController
@RequestMapping("/api/v1/admin/notifications/channel-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ChannelPolicyController {

  private final ChannelPolicyGateway channelPolicyGateway;

  @GetMapping
  public ResponseEntity<ApiResponse<List<ChannelPolicyResponse>>> listAll() {
    return ResponseEntity.ok(ApiResponse.success(channelPolicyGateway.listAll()));
  }
}
