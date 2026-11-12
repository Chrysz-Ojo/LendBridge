package com.lendbridge.adminbff.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lendbridge.adminbff.authentication.RoleAssignmentGateway;
import com.lendbridge.adminbff.dto.ApprovalResponse;
import com.lendbridge.adminbff.dto.ChannelPolicyRequest;
import com.lendbridge.adminbff.dto.DecideApprovalRequest;
import com.lendbridge.adminbff.dto.LoanReviewDecisionRequest;
import com.lendbridge.adminbff.dto.ReconciliationRunRequest;
import com.lendbridge.adminbff.dto.RaiseApprovalRequest;
import com.lendbridge.adminbff.dto.Role;
import com.lendbridge.adminbff.dto.RoleAssignmentRequest;
import com.lendbridge.adminbff.loan.LoanReviewGateway;
import com.lendbridge.adminbff.model.ApprovalActionType;
import com.lendbridge.adminbff.model.ApprovalRequest;
import com.lendbridge.adminbff.model.ApprovalStatus;
import com.lendbridge.adminbff.notification.ChannelPolicyGateway;
import com.lendbridge.adminbff.outbox.KafkaTopics;
import com.lendbridge.adminbff.payment.ReconciliationGateway;
import com.lendbridge.adminbff.repository.ApprovalRequestRepository;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnauthorizedException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ApprovalServiceImplTest {

  private ApprovalRequestRepository approvalRequestRepository;
  private RoleAssignmentGateway roleAssignmentGateway;
  private LoanReviewGateway loanReviewGateway;
  private ChannelPolicyGateway channelPolicyGateway;
  private ReconciliationGateway reconciliationGateway;
  private OutboxEventWriter outboxEventWriter;
  private ApprovalServiceImpl service;

  @BeforeEach
  void setUp() {
    approvalRequestRepository = mock(ApprovalRequestRepository.class);
    roleAssignmentGateway = mock(RoleAssignmentGateway.class);
    loanReviewGateway = mock(LoanReviewGateway.class);
    channelPolicyGateway = mock(ChannelPolicyGateway.class);
    reconciliationGateway = mock(ReconciliationGateway.class);
    outboxEventWriter = mock(OutboxEventWriter.class);
    service =
        new ApprovalServiceImpl(
            approvalRequestRepository,
            roleAssignmentGateway,
            loanReviewGateway,
            channelPolicyGateway,
            reconciliationGateway,
            outboxEventWriter,
            new ObjectMapper().registerModule(new JavaTimeModule()));
  }

  private static Authentication authWithRole(String role) {
    Authentication authentication = mock(Authentication.class);
    doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + role))).when(authentication).getAuthorities();
    return authentication;
  }

  private ApprovalRequest pending(ApprovalActionType type, String payload, UUID raisedBy) {
    return ApprovalRequest.builder()
        .id(UUID.randomUUID())
        .actionType(type)
        .actionPayload(payload)
        .raisedByUserId(raisedBy)
        .status(ApprovalStatus.PENDING)
        .createdAt(Instant.now())
        .build();
  }

  private ApprovalRequest pendingRoleAssignment(UUID raisedBy, UUID targetUserId) {
    return pending(
        ApprovalActionType.ROLE_ASSIGNMENT,
        "{\"targetUserId\":\"" + targetUserId + "\",\"role\":\"SUPER_ADMIN\"}",
        raisedBy);
  }

  @Test
  void raise_createsPendingApproval() {
    RaiseApprovalRequest request =
        new RaiseApprovalRequest(
            ApprovalActionType.ROLE_ASSIGNMENT, "{\"targetUserId\":\"" + UUID.randomUUID() + "\",\"role\":\"SUPPORT\"}");
    UUID raisedBy = UUID.randomUUID();

    ApprovalResponse response = service.raise(request, raisedBy, authWithRole("SUPER_ADMIN"));

    assertThat(response.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    assertThat(response.getRaisedByUserId()).isEqualTo(raisedBy);
    assertThat(response.getActionType()).isEqualTo(ApprovalActionType.ROLE_ASSIGNMENT);
    verify(approvalRequestRepository).save(any(ApprovalRequest.class));
  }

  @Test
  void raise_withoutRequiredRole_isRejected() {
    RaiseApprovalRequest request =
        new RaiseApprovalRequest(
            ApprovalActionType.ROLE_ASSIGNMENT, "{\"targetUserId\":\"" + UUID.randomUUID() + "\",\"role\":\"SUPPORT\"}");

    assertThatThrownBy(() -> service.raise(request, UUID.randomUUID(), authWithRole("SUPPORT")))
        .isInstanceOf(UnauthorizedException.class);
    verify(approvalRequestRepository, never()).save(any());
  }

  @Test
  void decide_approve_roleAssignment_dispatchesAndPublishesApproved() {
    UUID raisedBy = UUID.randomUUID();
    UUID decidedBy = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    ApprovalRequest approval = pendingRoleAssignment(raisedBy, targetUserId);
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    ApprovalResponse response =
        service.decide(
            approval.getId(), new DecideApprovalRequest(true, "looks right"), decidedBy, authWithRole("SUPER_ADMIN"));

    assertThat(response.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    assertThat(response.getDecidedByUserId()).isEqualTo(decidedBy);

    ArgumentCaptor<RoleAssignmentRequest> requestCaptor = ArgumentCaptor.forClass(RoleAssignmentRequest.class);
    verify(roleAssignmentGateway).assignRole(eq(targetUserId), requestCaptor.capture());
    assertThat(requestCaptor.getValue().getRole()).isEqualTo(Role.SUPER_ADMIN);

    verify(outboxEventWriter)
        .write(eq("ApprovalRequest"), eq(approval.getId().toString()), eq(KafkaTopics.ADMIN_ACTION_APPROVED), any());
  }

  @Test
  void decide_reject_publishesRejectedWithNoDispatch() {
    UUID raisedBy = UUID.randomUUID();
    UUID decidedBy = UUID.randomUUID();
    ApprovalRequest approval = pendingRoleAssignment(raisedBy, UUID.randomUUID());
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    ApprovalResponse response =
        service.decide(
            approval.getId(),
            new DecideApprovalRequest(false, "not appropriate"),
            decidedBy,
            authWithRole("SUPER_ADMIN"));

    assertThat(response.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
    verify(roleAssignmentGateway, never()).assignRole(any(), any());
    verify(outboxEventWriter)
        .write(eq("ApprovalRequest"), eq(approval.getId().toString()), eq(KafkaTopics.ADMIN_ACTION_REJECTED), any());
  }

  @Test
  void decide_raiserEqualsDecider_isRejected() {
    UUID sameUser = UUID.randomUUID();
    ApprovalRequest approval = pendingRoleAssignment(sameUser, UUID.randomUUID());
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    assertThatThrownBy(
            () ->
                service.decide(
                    approval.getId(), new DecideApprovalRequest(true, null), sameUser, authWithRole("SUPER_ADMIN")))
        .isInstanceOf(ConflictException.class);
    verify(roleAssignmentGateway, never()).assignRole(any(), any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void decide_alreadyDecided_isRejected() {
    ApprovalRequest approval = pendingRoleAssignment(UUID.randomUUID(), UUID.randomUUID());
    approval.setStatus(ApprovalStatus.APPROVED);
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    assertThatThrownBy(
            () ->
                service.decide(
                    approval.getId(),
                    new DecideApprovalRequest(true, null),
                    UUID.randomUUID(),
                    authWithRole("SUPER_ADMIN")))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void decide_unknownApproval_notFound() {
    UUID unknownId = UUID.randomUUID();
    when(approvalRequestRepository.findById(unknownId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.decide(
                    unknownId, new DecideApprovalRequest(true, null), UUID.randomUUID(), authWithRole("SUPER_ADMIN")))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void decide_withoutRequiredRole_isRejected() {
    UUID raisedBy = UUID.randomUUID();
    ApprovalRequest approval = pendingRoleAssignment(raisedBy, UUID.randomUUID());
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    assertThatThrownBy(
            () ->
                service.decide(
                    approval.getId(),
                    new DecideApprovalRequest(true, null),
                    UUID.randomUUID(),
                    authWithRole("SUPPORT")))
        .isInstanceOf(UnauthorizedException.class);
    verify(roleAssignmentGateway, never()).assignRole(any(), any());
  }

  @Test
  void decide_approve_loanReviewDecision_dispatchesToLoanReviewGateway() {
    UUID loanId = UUID.randomUUID();
    ApprovalRequest approval =
        pending(
            ApprovalActionType.LOAN_REVIEW_DECISION,
            "{\"loanId\":\"" + loanId + "\",\"decision\":\"APPROVE\",\"reason\":null}",
            UUID.randomUUID());
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    service.decide(
        approval.getId(), new DecideApprovalRequest(true, "second opinion agrees"), UUID.randomUUID(), authWithRole("RISK_OFFICER"));

    ArgumentCaptor<LoanReviewDecisionRequest> captor = ArgumentCaptor.forClass(LoanReviewDecisionRequest.class);
    verify(loanReviewGateway).decide(eq(loanId), captor.capture());
    assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
  }

  @Test
  void decide_approve_channelPolicyUpdate_dispatchesToChannelPolicyGateway() {
    ApprovalRequest approval =
        pending(
            ApprovalActionType.CHANNEL_POLICY_UPDATE,
            "{\"eventType\":\"LOAN_LISTED\",\"emailEnabled\":true,\"smsEnabled\":false,\"inAppEnabled\":true}",
            UUID.randomUUID());
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    service.decide(
        approval.getId(), new DecideApprovalRequest(true, null), UUID.randomUUID(), authWithRole("SUPER_ADMIN"));

    ArgumentCaptor<ChannelPolicyRequest> captor = ArgumentCaptor.forClass(ChannelPolicyRequest.class);
    verify(channelPolicyGateway).upsert(eq("LOAN_LISTED"), captor.capture());
    assertThat(captor.getValue().getEmailEnabled()).isTrue();
    assertThat(captor.getValue().getSmsEnabled()).isFalse();
  }

  @Test
  void decide_approve_reconciliationRun_dispatchesToReconciliationGateway() {
    ApprovalRequest approval =
        pending(
            ApprovalActionType.RECONCILIATION_RUN,
            "{\"provider\":\"PAYSTACK\",\"periodStart\":\"2026-11-01T00:00:00Z\",\"periodEnd\":\"2026-11-12T00:00:00Z\"}",
            UUID.randomUUID());
    when(approvalRequestRepository.findById(approval.getId())).thenReturn(Optional.of(approval));

    service.decide(
        approval.getId(), new DecideApprovalRequest(true, null), UUID.randomUUID(), authWithRole("FINANCE"));

    ArgumentCaptor<ReconciliationRunRequest> captor = ArgumentCaptor.forClass(ReconciliationRunRequest.class);
    verify(reconciliationGateway).runReconciliation(captor.capture());
    assertThat(captor.getValue().getProvider()).isEqualTo("PAYSTACK");
  }
}
