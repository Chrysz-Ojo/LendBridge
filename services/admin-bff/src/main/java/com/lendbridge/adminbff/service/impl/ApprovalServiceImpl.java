package com.lendbridge.adminbff.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.adminbff.authentication.RoleAssignmentGateway;
import com.lendbridge.adminbff.dto.ApprovalResponse;
import com.lendbridge.adminbff.dto.ChannelPolicyRequest;
import com.lendbridge.adminbff.dto.ChannelPolicyUpdateActionPayload;
import com.lendbridge.adminbff.dto.DecideApprovalRequest;
import com.lendbridge.adminbff.dto.LoanReviewDecisionActionPayload;
import com.lendbridge.adminbff.dto.LoanReviewDecisionRequest;
import com.lendbridge.adminbff.dto.ReconciliationRunRequest;
import com.lendbridge.adminbff.dto.RaiseApprovalRequest;
import com.lendbridge.adminbff.dto.RoleAssignmentActionPayload;
import com.lendbridge.adminbff.dto.RoleAssignmentRequest;
import com.lendbridge.adminbff.loan.LoanReviewGateway;
import com.lendbridge.adminbff.model.ApprovalActionType;
import com.lendbridge.adminbff.model.ApprovalRequest;
import com.lendbridge.adminbff.model.ApprovalStatus;
import com.lendbridge.adminbff.notification.ChannelPolicyGateway;
import com.lendbridge.adminbff.outbox.AdminActionPayload;
import com.lendbridge.adminbff.outbox.KafkaTopics;
import com.lendbridge.adminbff.payment.ReconciliationGateway;
import com.lendbridge.adminbff.repository.ApprovalRequestRepository;
import com.lendbridge.adminbff.security.CurrentUser;
import com.lendbridge.adminbff.service.ApprovalService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The generic maker-checker flow (PRD E11-S3). {@code decide(...)}'s core invariant — a different
 * admin must decide than the one who raised it — is the entire point of maker-checker; on top of
 * that, both {@code raise(...)} and {@code decide(...)} independently check that the caller holds
 * the specific role {@link ApprovalActionType#requiredRole()} demands for that action type (
 * {@code ApprovalController} itself only gates on "any staff role" generically — the per-action
 * check lives here, where the action type is known). Dispatch on approve is a simple switch over
 * {@link ApprovalActionType}, four cases today — see ADR 0023 for how to extend this with a new
 * action type.
 */
@Service
public class ApprovalServiceImpl implements ApprovalService {

  private final ApprovalRequestRepository approvalRequestRepository;
  private final RoleAssignmentGateway roleAssignmentGateway;
  private final LoanReviewGateway loanReviewGateway;
  private final ChannelPolicyGateway channelPolicyGateway;
  private final ReconciliationGateway reconciliationGateway;
  private final OutboxEventWriter outboxEventWriter;
  private final ObjectMapper objectMapper;

  public ApprovalServiceImpl(
      ApprovalRequestRepository approvalRequestRepository,
      RoleAssignmentGateway roleAssignmentGateway,
      LoanReviewGateway loanReviewGateway,
      ChannelPolicyGateway channelPolicyGateway,
      ReconciliationGateway reconciliationGateway,
      OutboxEventWriter outboxEventWriter,
      ObjectMapper objectMapper) {
    this.approvalRequestRepository = approvalRequestRepository;
    this.roleAssignmentGateway = roleAssignmentGateway;
    this.loanReviewGateway = loanReviewGateway;
    this.channelPolicyGateway = channelPolicyGateway;
    this.reconciliationGateway = reconciliationGateway;
    this.outboxEventWriter = outboxEventWriter;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public ApprovalResponse raise(RaiseApprovalRequest request, UUID raisedByUserId, Authentication authentication) {
    assertHoldsRequiredRole(request.getActionType(), authentication);

    Instant now = Instant.now();
    ApprovalRequest approval =
        ApprovalRequest.builder()
            .id(UuidV7.generate())
            .actionType(request.getActionType())
            .actionPayload(request.getActionPayload())
            .raisedByUserId(raisedByUserId)
            .status(ApprovalStatus.PENDING)
            .createdAt(now)
            .build();
    approvalRequestRepository.save(approval);
    return toResponse(approval);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ApprovalResponse> findPending() {
    return approvalRequestRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING).stream()
        .map(ApprovalServiceImpl::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public ApprovalResponse decide(
      UUID approvalId, DecideApprovalRequest request, UUID decidedByUserId, Authentication authentication) {
    ApprovalRequest approval =
        approvalRequestRepository
            .findById(approvalId)
            .orElseThrow(() -> new NotFoundException("APPROVAL_NOT_FOUND", "Approval request not found"));

    if (approval.getStatus() != ApprovalStatus.PENDING) {
      throw new ConflictException("APPROVAL_ALREADY_DECIDED", "This approval request has already been decided");
    }
    if (approval.getRaisedByUserId().equals(decidedByUserId)) {
      throw new ConflictException(
          "MAKER_CANNOT_BE_CHECKER", "The admin who raised this request cannot also approve or reject it");
    }
    assertHoldsRequiredRole(approval.getActionType(), authentication);

    Instant now = Instant.now();
    boolean approve = Boolean.TRUE.equals(request.getApprove());
    approval.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
    approval.setDecidedByUserId(decidedByUserId);
    approval.setDecisionReason(request.getReason());
    approval.setDecidedAt(now);

    if (approve) {
      execute(approval);
    }
    approvalRequestRepository.save(approval);

    outboxEventWriter.write(
        "ApprovalRequest",
        approval.getId().toString(),
        approve ? KafkaTopics.ADMIN_ACTION_APPROVED : KafkaTopics.ADMIN_ACTION_REJECTED,
        AdminActionPayload.builder()
            .approvalId(approval.getId())
            .actionType(approval.getActionType().name())
            .raisedByUserId(approval.getRaisedByUserId())
            .decidedByUserId(decidedByUserId)
            .status(approval.getStatus().name())
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());

    return toResponse(approval);
  }

  /** {@code ApprovalController} accepts any staff role so the generic raise/list/decide API
   * isn't pre-gated to a single role — but each action type still demands its own specific role,
   * checked here where the action type is actually known. */
  private static void assertHoldsRequiredRole(ApprovalActionType actionType, Authentication authentication) {
    if (!CurrentUser.hasRole(authentication, actionType.requiredRole())) {
      throw new UnauthorizedException(
          "ROLE_REQUIRED_FOR_ACTION",
          "This action requires the " + actionType.requiredRole() + " role");
    }
  }

  /** Performs the real action once approved. Four cases today — add a new
   * {@code ApprovalActionType} and a matching branch here to extend maker-checker further. */
  private void execute(ApprovalRequest approval) {
    switch (approval.getActionType()) {
      case ROLE_ASSIGNMENT -> {
        RoleAssignmentActionPayload payload = parsePayload(approval.getActionPayload(), RoleAssignmentActionPayload.class);
        roleAssignmentGateway.assignRole(payload.getTargetUserId(), new RoleAssignmentRequest(payload.getRole()));
      }
      case LOAN_REVIEW_DECISION -> {
        LoanReviewDecisionActionPayload payload =
            parsePayload(approval.getActionPayload(), LoanReviewDecisionActionPayload.class);
        loanReviewGateway.decide(
            payload.getLoanId(), new LoanReviewDecisionRequest(payload.getDecision(), payload.getReason()));
      }
      case CHANNEL_POLICY_UPDATE -> {
        ChannelPolicyUpdateActionPayload payload =
            parsePayload(approval.getActionPayload(), ChannelPolicyUpdateActionPayload.class);
        channelPolicyGateway.upsert(
            payload.getEventType(),
            new ChannelPolicyRequest(payload.getEmailEnabled(), payload.getSmsEnabled(), payload.getInAppEnabled()));
      }
      case RECONCILIATION_RUN -> {
        ReconciliationRunRequest payload = parsePayload(approval.getActionPayload(), ReconciliationRunRequest.class);
        reconciliationGateway.runReconciliation(payload);
      }
    }
  }

  private <T> T parsePayload(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (JsonProcessingException e) {
      throw new ConflictException("INVALID_ACTION_PAYLOAD", "actionPayload is not valid for this actionType: " + e.getMessage());
    }
  }

  private static ApprovalResponse toResponse(ApprovalRequest approval) {
    return ApprovalResponse.builder()
        .id(approval.getId())
        .actionType(approval.getActionType())
        .actionPayload(approval.getActionPayload())
        .raisedByUserId(approval.getRaisedByUserId())
        .status(approval.getStatus())
        .decidedByUserId(approval.getDecidedByUserId())
        .decisionReason(approval.getDecisionReason())
        .createdAt(approval.getCreatedAt())
        .decidedAt(approval.getDecidedAt())
        .build();
  }
}
