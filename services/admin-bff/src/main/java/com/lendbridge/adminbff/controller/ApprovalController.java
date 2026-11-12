package com.lendbridge.adminbff.controller;

import com.lendbridge.adminbff.dto.ApprovalResponse;
import com.lendbridge.adminbff.dto.DecideApprovalRequest;
import com.lendbridge.adminbff.dto.RaiseApprovalRequest;
import com.lendbridge.adminbff.security.CurrentUser;
import com.lendbridge.adminbff.service.ApprovalService;
import com.lendbridge.starter.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The generic maker-checker API (PRD E11-S3) — the only path to any of the four wired
 * {@code ApprovalActionType}s (role assignment, loan review decisions, channel-policy updates,
 * reconciliation runs); none of them has a direct passthrough endpoint any more (see each
 * action's gateway javadoc).
 *
 * <p>Gated here to "any staff role" ({@code hasAnyRole}), deliberately broad: a {@code FINANCE}
 * officer needs to raise/decide a {@code RECONCILIATION_RUN} approval just as much as a
 * {@code SUPER_ADMIN} needs to raise/decide a {@code ROLE_ASSIGNMENT} one, and this controller has
 * no way to know in advance which action type a given request concerns. The specific role each
 * action type actually demands ({@link com.lendbridge.adminbff.model.ApprovalActionType#requiredRole()})
 * is enforced inside {@code ApprovalServiceImpl}, where the action type is known — so a
 * {@code SUPPORT} caller can reach this endpoint but still gets rejected the moment they try to
 * raise or decide a {@code ROLE_ASSIGNMENT}. */
@RestController
@RequestMapping("/api/v1/admin/approvals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','RISK_OFFICER','FINANCE','SUPPORT','KYC_OFFICER')")
public class ApprovalController {

  private final ApprovalService approvalService;

  @PostMapping
  public ResponseEntity<ApiResponse<ApprovalResponse>> raise(
      @Valid @RequestBody RaiseApprovalRequest request, Authentication authentication) {
    UUID raisedByUserId = CurrentUser.idFrom(authentication);
    return ResponseEntity.ok(ApiResponse.success(approvalService.raise(request, raisedByUserId, authentication)));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<ApprovalResponse>>> findPending() {
    return ResponseEntity.ok(ApiResponse.success(approvalService.findPending()));
  }

  @PostMapping("/{id}/decide")
  public ResponseEntity<ApiResponse<ApprovalResponse>> decide(
      @PathVariable UUID id, @Valid @RequestBody DecideApprovalRequest request, Authentication authentication) {
    UUID decidedByUserId = CurrentUser.idFrom(authentication);
    return ResponseEntity.ok(
        ApiResponse.success(approvalService.decide(id, request, decidedByUserId, authentication)));
  }
}
