package com.lendbridge.risk.service.impl;

import com.lendbridge.risk.dto.KycRejectedEvent;
import com.lendbridge.risk.dto.KycVerifiedEvent;
import com.lendbridge.risk.dto.LoanSubmittedEvent;
import com.lendbridge.risk.dto.RiskAssessmentDetailResponse;
import com.lendbridge.risk.dto.RiskGradeResponse;
import com.lendbridge.risk.model.BorrowerTrustProfile;
import com.lendbridge.risk.model.RiskAssessment;
import com.lendbridge.risk.model.RiskAssessmentStatus;
import com.lendbridge.risk.model.RiskGrade;
import com.lendbridge.risk.model.TrustProfileStatus;
import com.lendbridge.risk.model.TrustTier;
import com.lendbridge.risk.outbox.KafkaTopics;
import com.lendbridge.risk.outbox.RiskGradedPayload;
import com.lendbridge.risk.repository.BorrowerTrustProfileRepository;
import com.lendbridge.risk.repository.RiskAssessmentRepository;
import com.lendbridge.risk.service.RiskAssessmentService;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Symmetric retroactive-grading design (see the approved plan's processing logic and data model):
 * {@code loan.submitted} and {@code kyc.verified}/{@code kyc.rejected} are independent event
 * streams with no guaranteed ordering, so a loan submitted before its borrower's KYC clears is
 * parked at {@link RiskAssessmentStatus#PENDING_KYC} rather than dropped, and graded retroactively
 * once the trust profile resolves. The two amount thresholds behind {@code rules-v1}'s amount band
 * are env-configurable (CLAUDE.md's "configurable limits" rule), mixed into the constructor via
 * {@code @Value} — same pattern as loan-service's {@code LoanServiceImpl}.
 */
@Service
public class RiskAssessmentServiceImpl implements RiskAssessmentService {

  private final RiskAssessmentRepository riskAssessmentRepository;
  private final BorrowerTrustProfileRepository borrowerTrustProfileRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final long amountThreshold1Kobo;
  private final long amountThreshold2Kobo;

  public RiskAssessmentServiceImpl(
      RiskAssessmentRepository riskAssessmentRepository,
      BorrowerTrustProfileRepository borrowerTrustProfileRepository,
      OutboxEventWriter outboxEventWriter,
      @Value("${risk.grading.amount-threshold1-kobo}") long amountThreshold1Kobo,
      @Value("${risk.grading.amount-threshold2-kobo}") long amountThreshold2Kobo) {
    this.riskAssessmentRepository = riskAssessmentRepository;
    this.borrowerTrustProfileRepository = borrowerTrustProfileRepository;
    this.outboxEventWriter = outboxEventWriter;
    this.amountThreshold1Kobo = amountThreshold1Kobo;
    this.amountThreshold2Kobo = amountThreshold2Kobo;
  }

  @Override
  @Transactional(readOnly = true)
  public RiskGradeResponse getGrade(UUID loanId) {
    RiskAssessment assessment = findOrThrow(loanId);
    return RiskGradeResponse.builder()
        .loanId(assessment.getLoanId())
        .status(assessment.getStatus())
        .grade(assessment.getGrade())
        .modelVersion(assessment.getModelVersion())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public RiskAssessmentDetailResponse getDetail(UUID loanId) {
    RiskAssessment assessment = findOrThrow(loanId);
    return RiskAssessmentDetailResponse.builder()
        .loanId(assessment.getLoanId())
        .borrowerUserId(assessment.getBorrowerUserId())
        .status(assessment.getStatus())
        .grade(assessment.getGrade())
        .modelVersion(assessment.getModelVersion())
        .expectedDefaultBand(
            assessment.getGrade() == null
                ? null
                : RiskGradeCalculator.expectedDefaultBandLabel(assessment.getGrade()))
        .trustTierUsed(assessment.getTrustTierUsed())
        .amountKobo(assessment.getAmountKobo())
        .tenorMonths(assessment.getTenorMonths())
        .interestRateBps(assessment.getInterestRateBps())
        .kycRejectionReason(assessment.getKycRejectionReason())
        .build();
  }

  @Override
  @Transactional
  public void handleLoanSubmitted(LoanSubmittedEvent event) {
    Instant now = Instant.now();
    Optional<BorrowerTrustProfile> profile = borrowerTrustProfileRepository.findById(event.getBorrowerUserId());

    RiskAssessment.RiskAssessmentBuilder builder =
        RiskAssessment.builder()
            .loanId(event.getLoanId())
            .borrowerUserId(event.getBorrowerUserId())
            .amountKobo(event.getAmountKobo())
            .tenorMonths(event.getTenorMonths())
            .interestRateBps(event.getInterestRateBps())
            .createdAt(now)
            .updatedAt(now);

    if (profile.isEmpty()) {
      riskAssessmentRepository.save(builder.status(RiskAssessmentStatus.PENDING_KYC).build());
      return;
    }

    BorrowerTrustProfile trustProfile = profile.get();
    if (trustProfile.getStatus() == TrustProfileStatus.REJECTED) {
      riskAssessmentRepository.save(
          builder
              .status(RiskAssessmentStatus.KYC_REJECTED)
              .kycRejectionReason(trustProfile.getReason())
              .build());
      return;
    }

    RiskAssessment assessment = builder.status(RiskAssessmentStatus.PENDING_KYC).build();
    gradeAndPublish(assessment, trustProfile.getTrustTier(), now);
  }

  @Override
  @Transactional
  public void handleKycVerified(KycVerifiedEvent event) {
    Instant now = Instant.now();
    BorrowerTrustProfile profile = upsertProfileGuardingOutOfOrder(event.getUserId(), event.getOccurredAt(), now);
    if (profile == null) {
      return;
    }
    profile.setStatus(TrustProfileStatus.VERIFIED);
    profile.setTrustTier(event.getTrustTier());
    profile.setReason(null);
    profile.setSourceOccurredAt(event.getOccurredAt());
    profile.setUpdatedAt(now);
    borrowerTrustProfileRepository.save(profile);

    List<RiskAssessment> pending =
        riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(
            event.getUserId(), RiskAssessmentStatus.PENDING_KYC);
    for (RiskAssessment assessment : pending) {
      gradeAndPublish(assessment, event.getTrustTier(), now);
    }
  }

  @Override
  @Transactional
  public void handleKycRejected(KycRejectedEvent event) {
    Instant now = Instant.now();
    BorrowerTrustProfile profile = upsertProfileGuardingOutOfOrder(event.getUserId(), event.getOccurredAt(), now);
    if (profile == null) {
      return;
    }
    profile.setStatus(TrustProfileStatus.REJECTED);
    profile.setTrustTier(null);
    profile.setReason(event.getReason());
    profile.setSourceOccurredAt(event.getOccurredAt());
    profile.setUpdatedAt(now);
    borrowerTrustProfileRepository.save(profile);

    // No Kafka event published here — there's no "ungraded" event in the catalogue, and per PRD
    // E5-S3 only verified borrowers get a usable grade, so this is a local-only audit record.
    List<RiskAssessment> pending =
        riskAssessmentRepository.findAllByBorrowerUserIdAndStatus(
            event.getUserId(), RiskAssessmentStatus.PENDING_KYC);
    for (RiskAssessment assessment : pending) {
      assessment.setStatus(RiskAssessmentStatus.KYC_REJECTED);
      assessment.setKycRejectionReason(event.getReason());
      assessment.setUpdatedAt(now);
      riskAssessmentRepository.save(assessment);
    }
  }

  /** Returns {@code null} (a no-op) if an existing profile's {@code sourceOccurredAt} is already
   * newer than the incoming event — the out-of-order-redelivery guard from the approved plan's
   * data model. Otherwise returns the profile to mutate (existing or newly built). */
  private BorrowerTrustProfile upsertProfileGuardingOutOfOrder(UUID userId, Instant eventOccurredAt, Instant now) {
    Optional<BorrowerTrustProfile> existing = borrowerTrustProfileRepository.findById(userId);
    if (existing.isPresent() && existing.get().getSourceOccurredAt().isAfter(eventOccurredAt)) {
      return null;
    }
    return existing.orElseGet(() -> BorrowerTrustProfile.builder().userId(userId).build());
  }

  private void gradeAndPublish(RiskAssessment assessment, TrustTier trustTier, Instant now) {
    RiskGrade grade =
        RiskGradeCalculator.compute(
            RiskGradeCalculationRequest.builder()
                .trustTier(trustTier)
                .amountKobo(assessment.getAmountKobo())
                .tenorMonths(assessment.getTenorMonths())
                .interestRateBps(assessment.getInterestRateBps())
                .amountThreshold1Kobo(amountThreshold1Kobo)
                .amountThreshold2Kobo(amountThreshold2Kobo)
                .build());

    assessment.setStatus(RiskAssessmentStatus.GRADED);
    assessment.setGrade(grade);
    assessment.setModelVersion(RiskGradeCalculator.MODEL_VERSION);
    assessment.setTrustTierUsed(trustTier);
    assessment.setKycRejectionReason(null);
    assessment.setGradedAt(now);
    assessment.setUpdatedAt(now);
    riskAssessmentRepository.save(assessment);

    outboxEventWriter.write(
        "RiskAssessment",
        assessment.getLoanId().toString(),
        KafkaTopics.RISK_GRADED,
        RiskGradedPayload.builder()
            .loanId(assessment.getLoanId())
            .borrowerUserId(assessment.getBorrowerUserId())
            .grade(grade.name())
            .modelVersion(RiskGradeCalculator.MODEL_VERSION)
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());
  }

  private RiskAssessment findOrThrow(UUID loanId) {
    return riskAssessmentRepository
        .findById(loanId)
        .orElseThrow(() -> new NotFoundException("RISK_ASSESSMENT_NOT_FOUND", "Risk assessment not found"));
  }
}
