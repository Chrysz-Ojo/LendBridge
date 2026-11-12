package com.lendbridge.risk.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.risk.dto.KycRejectedEvent;
import com.lendbridge.risk.dto.KycVerifiedEvent;
import com.lendbridge.risk.dto.LoanSubmittedEvent;
import com.lendbridge.risk.model.BorrowerTrustProfile;
import com.lendbridge.risk.model.RiskAssessment;
import com.lendbridge.risk.model.RiskAssessmentStatus;
import com.lendbridge.risk.model.TrustProfileStatus;
import com.lendbridge.risk.model.TrustTier;
import com.lendbridge.risk.outbox.RiskGradedPayload;
import com.lendbridge.risk.repository.BorrowerTrustProfileRepository;
import com.lendbridge.risk.repository.RiskAssessmentRepository;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RiskAssessmentServiceImplTest {

  private static final long THRESHOLD1 = 50_000_000L;
  private static final long THRESHOLD2 = 200_000_000L;

  private RiskAssessmentRepository riskAssessmentRepository;
  private BorrowerTrustProfileRepository borrowerTrustProfileRepository;
  private OutboxEventWriter outboxEventWriter;
  private RiskAssessmentServiceImpl service;

  @BeforeEach
  void setUp() {
    riskAssessmentRepository = mock(RiskAssessmentRepository.class);
    borrowerTrustProfileRepository = mock(BorrowerTrustProfileRepository.class);
    outboxEventWriter = mock(OutboxEventWriter.class);
    service =
        new RiskAssessmentServiceImpl(
            riskAssessmentRepository, borrowerTrustProfileRepository, outboxEventWriter, THRESHOLD1, THRESHOLD2);
  }

  private LoanSubmittedEvent loanSubmitted(UUID borrowerUserId) {
    return LoanSubmittedEvent.builder()
        .loanId(UUID.randomUUID())
        .borrowerUserId(borrowerUserId)
        .amountKobo(1_000_000L)
        .tenorMonths(12)
        .interestRateBps(1500)
        .occurredAt(Instant.now())
        .build();
  }

  @Test
  void handleLoanSubmitted_noExistingProfile_parksAsPendingKyc() {
    UUID borrowerUserId = UUID.randomUUID();
    when(borrowerTrustProfileRepository.findById(borrowerUserId)).thenReturn(Optional.empty());

    service.handleLoanSubmitted(loanSubmitted(borrowerUserId));

    ArgumentCaptor<RiskAssessment> captor = ArgumentCaptor.forClass(RiskAssessment.class);
    verify(riskAssessmentRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(RiskAssessmentStatus.PENDING_KYC);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void handleLoanSubmitted_verifiedProfile_gradesImmediatelyAndPublishes() {
    UUID borrowerUserId = UUID.randomUUID();
    BorrowerTrustProfile profile =
        BorrowerTrustProfile.builder()
            .userId(borrowerUserId)
            .status(TrustProfileStatus.VERIFIED)
            .trustTier(TrustTier.TIER_2)
            .sourceOccurredAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .updatedAt(Instant.now())
            .build();
    when(borrowerTrustProfileRepository.findById(borrowerUserId)).thenReturn(Optional.of(profile));

    service.handleLoanSubmitted(loanSubmitted(borrowerUserId));

    ArgumentCaptor<RiskAssessment> captor = ArgumentCaptor.forClass(RiskAssessment.class);
    verify(riskAssessmentRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(RiskAssessmentStatus.GRADED);
    assertThat(captor.getValue().getGrade()).isNotNull();
    assertThat(captor.getValue().getTrustTierUsed()).isEqualTo(TrustTier.TIER_2);

    ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(outboxEventWriter).write(any(), any(), any(), payloadCaptor.capture());
    assertThat(((RiskGradedPayload) payloadCaptor.getValue()).getLoanId()).isEqualTo(captor.getValue().getLoanId());
  }

  @Test
  void handleLoanSubmitted_rejectedProfile_recordsKycRejectedDirectlyNoEvent() {
    UUID borrowerUserId = UUID.randomUUID();
    BorrowerTrustProfile profile =
        BorrowerTrustProfile.builder()
            .userId(borrowerUserId)
            .status(TrustProfileStatus.REJECTED)
            .reason("document mismatch")
            .sourceOccurredAt(Instant.now().minus(1, ChronoUnit.DAYS))
            .updatedAt(Instant.now())
            .build();
    when(borrowerTrustProfileRepository.findById(borrowerUserId)).thenReturn(Optional.of(profile));

    service.handleLoanSubmitted(loanSubmitted(borrowerUserId));

    ArgumentCaptor<RiskAssessment> captor = ArgumentCaptor.forClass(RiskAssessment.class);
    verify(riskAssessmentRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(RiskAssessmentStatus.KYC_REJECTED);
    assertThat(captor.getValue().getKycRejectionReason()).isEqualTo("document mismatch");
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void handleKycVerified_noPendingAssessments_upsertsProfileOnly() {
    UUID userId = UUID.randomUUID();
    when(borrowerTrustProfileRepository.findById(userId)).thenReturn(Optional.empty());
    when(riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(userId, RiskAssessmentStatus.PENDING_KYC))
        .thenReturn(List.of());

    service.handleKycVerified(
        KycVerifiedEvent.builder().userId(userId).trustTier(TrustTier.TIER_1).occurredAt(Instant.now()).build());

    verify(borrowerTrustProfileRepository).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void handleKycVerified_onePendingAssessment_gradesAndPublishesOnce() {
    UUID userId = UUID.randomUUID();
    when(borrowerTrustProfileRepository.findById(userId)).thenReturn(Optional.empty());
    RiskAssessment pending = pendingAssessment(userId);
    when(riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(userId, RiskAssessmentStatus.PENDING_KYC))
        .thenReturn(List.of(pending));

    service.handleKycVerified(
        KycVerifiedEvent.builder().userId(userId).trustTier(TrustTier.TIER_1).occurredAt(Instant.now()).build());

    verify(riskAssessmentRepository).save(pending);
    assertThat(pending.getStatus()).isEqualTo(RiskAssessmentStatus.GRADED);
    verify(outboxEventWriter, times(1)).write(any(), any(), any(), any());
  }

  @Test
  void handleKycVerified_multiplePendingAssessments_gradesAndPublishesEach() {
    UUID userId = UUID.randomUUID();
    when(borrowerTrustProfileRepository.findById(userId)).thenReturn(Optional.empty());
    RiskAssessment first = pendingAssessment(userId);
    RiskAssessment second = pendingAssessment(userId);
    when(riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(userId, RiskAssessmentStatus.PENDING_KYC))
        .thenReturn(List.of(first, second));

    service.handleKycVerified(
        KycVerifiedEvent.builder().userId(userId).trustTier(TrustTier.TIER_3).occurredAt(Instant.now()).build());

    assertThat(first.getStatus()).isEqualTo(RiskAssessmentStatus.GRADED);
    assertThat(second.getStatus()).isEqualTo(RiskAssessmentStatus.GRADED);
    verify(outboxEventWriter, times(2)).write(any(), any(), any(), any());
  }

  @Test
  void handleKycVerified_outOfOrderRedelivery_isANoOp() {
    UUID userId = UUID.randomUUID();
    Instant staleEventTime = Instant.now().minus(1, ChronoUnit.DAYS);
    BorrowerTrustProfile existing =
        BorrowerTrustProfile.builder()
            .userId(userId)
            .status(TrustProfileStatus.VERIFIED)
            .trustTier(TrustTier.TIER_2)
            .sourceOccurredAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(borrowerTrustProfileRepository.findById(userId)).thenReturn(Optional.of(existing));

    service.handleKycVerified(
        KycVerifiedEvent.builder().userId(userId).trustTier(TrustTier.TIER_1).occurredAt(staleEventTime).build());

    verify(borrowerTrustProfileRepository, never()).save(any());
    verify(riskAssessmentRepository, never())
        .findAllByBorrowerUserIdAndStatus(any(), any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void handleKycRejected_noPendingAssessments_upsertsProfileOnlyNoEvent() {
    UUID userId = UUID.randomUUID();
    when(borrowerTrustProfileRepository.findById(userId)).thenReturn(Optional.empty());
    when(riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(userId, RiskAssessmentStatus.PENDING_KYC))
        .thenReturn(List.of());

    service.handleKycRejected(
        KycRejectedEvent.builder().userId(userId).reason("selfie mismatch").occurredAt(Instant.now()).build());

    ArgumentCaptor<BorrowerTrustProfile> captor = ArgumentCaptor.forClass(BorrowerTrustProfile.class);
    verify(borrowerTrustProfileRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(TrustProfileStatus.REJECTED);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void handleKycRejected_onePendingAssessment_flipsToKycRejectedNoEvent() {
    UUID userId = UUID.randomUUID();
    when(borrowerTrustProfileRepository.findById(userId)).thenReturn(Optional.empty());
    RiskAssessment pending = pendingAssessment(userId);
    when(riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(userId, RiskAssessmentStatus.PENDING_KYC))
        .thenReturn(List.of(pending));

    service.handleKycRejected(
        KycRejectedEvent.builder().userId(userId).reason("selfie mismatch").occurredAt(Instant.now()).build());

    assertThat(pending.getStatus()).isEqualTo(RiskAssessmentStatus.KYC_REJECTED);
    assertThat(pending.getKycRejectionReason()).isEqualTo("selfie mismatch");
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void handleKycRejected_multiplePendingAssessments_flipsEachNoEvent() {
    UUID userId = UUID.randomUUID();
    when(borrowerTrustProfileRepository.findById(userId)).thenReturn(Optional.empty());
    RiskAssessment first = pendingAssessment(userId);
    RiskAssessment second = pendingAssessment(userId);
    when(riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(userId, RiskAssessmentStatus.PENDING_KYC))
        .thenReturn(List.of(first, second));

    service.handleKycRejected(
        KycRejectedEvent.builder().userId(userId).reason("expired document").occurredAt(Instant.now()).build());

    assertThat(first.getStatus()).isEqualTo(RiskAssessmentStatus.KYC_REJECTED);
    assertThat(second.getStatus()).isEqualTo(RiskAssessmentStatus.KYC_REJECTED);
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  private RiskAssessment pendingAssessment(UUID borrowerUserId) {
    Instant now = Instant.now();
    return RiskAssessment.builder()
        .loanId(UUID.randomUUID())
        .borrowerUserId(borrowerUserId)
        .status(RiskAssessmentStatus.PENDING_KYC)
        .amountKobo(1_000_000L)
        .tenorMonths(12)
        .interestRateBps(1500)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
