package com.lendbridge.kyc.service.impl;

import com.lendbridge.kyc.dto.ReviewDecisionRequest;
import com.lendbridge.kyc.dto.ReviewQueueItemResponse;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.model.BvnResult;
import com.lendbridge.kyc.model.LivenessResult;
import com.lendbridge.kyc.model.NinResult;
import com.lendbridge.kyc.model.TrustTier;
import com.lendbridge.kyc.model.VerificationCase;
import com.lendbridge.kyc.model.VerificationStatus;
import com.lendbridge.kyc.outbox.KafkaTopics;
import com.lendbridge.kyc.outbox.KycRejectedPayload;
import com.lendbridge.kyc.outbox.KycVerifiedPayload;
import com.lendbridge.kyc.repository.BvnResultRepository;
import com.lendbridge.kyc.repository.LivenessResultRepository;
import com.lendbridge.kyc.repository.NinResultRepository;
import com.lendbridge.kyc.repository.VerificationCaseRepository;
import com.lendbridge.kyc.service.ReviewService;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A case only ever reaches the queue ({@code reviewRequired=true}) with a mix of {@code PASSED}/
 * {@code MISMATCH} checks and zero {@code FAILED} ones — a {@code FAILED} check auto-rejects before
 * ever reaching this service (outcome routing rule 3). {@code REQUEST_INFO} is the only way a case
 * reaches {@code NEEDS_MORE_INFO} (outcome routing rule 2) and, like {@code PENDING}, still counts
 * as an "open case" for {@code IdempotencyOperations}' partial-unique-index check — resubmission
 * for a {@code NEEDS_MORE_INFO} case is a known follow-up gap, see this service's final report.
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {


  private final VerificationCaseRepository verificationCaseRepository;
  private final BvnResultRepository bvnResultRepository;
  private final NinResultRepository ninResultRepository;
  private final LivenessResultRepository livenessResultRepository;
  private final VerificationResponseAssembler responseAssembler;
  private final OutboxEventWriter outboxEventWriter;

  @Override
  @Transactional(readOnly = true)
  public List<ReviewQueueItemResponse> getQueue() {
    return verificationCaseRepository
        .findAllByStatusAndReviewRequiredTrueOrderByCreatedAtAsc(VerificationStatus.PENDING)
        .stream()
        .map(responseAssembler::toQueueItem)
        .toList();
  }

  @Override
  @Transactional
  public VerificationStatusResponse decide(UUID caseId, ReviewDecisionRequest request, UUID officerUserId) {
    VerificationCase verificationCase =
        verificationCaseRepository
            .findById(caseId)
            .orElseThrow(() -> new NotFoundException("VERIFICATION_CASE_NOT_FOUND", "Verification case not found"));
    if (verificationCase.getStatus() != VerificationStatus.PENDING) {
      throw new ConflictException("VERIFICATION_CASE_ALREADY_DECIDED", "This case has already been decided");
    }

    Instant now = Instant.now();
    switch (request.getDecision()) {
      case APPROVE -> approve(verificationCase, now);
      case REJECT -> reject(verificationCase, request, now);
      case REQUEST_INFO -> requestInfo(verificationCase, request);
    }
    verificationCase.setDecidedByUserId(officerUserId);
    verificationCase.setDecidedAt(now);
    verificationCase.setUpdatedAt(now);
    verificationCase.setReviewRequired(false);
    verificationCaseRepository.save(verificationCase);

    return responseAssembler.toStatusResponse(verificationCase);
  }


  private void approve(VerificationCase verificationCase, Instant now) {
    BvnResult bvnResult =
        bvnResultRepository
            .findByVerificationCaseId(verificationCase.getId())
            .orElseThrow(() -> new IllegalStateException("Missing BvnResult for case " + verificationCase.getId()));
    Optional<NinResult> ninResult = ninResultRepository.findByVerificationCaseId(verificationCase.getId());
    LivenessResult livenessResult =
        livenessResultRepository
            .findByVerificationCaseId(verificationCase.getId())
            .orElseThrow(() -> new IllegalStateException("Missing LivenessResult for case " + verificationCase.getId()));

    TrustTier trustTier =
        TrustTierCalculator.compute(
            TrustTierCalculationRequest.builder()
                .bvnOutcome(bvnResult.getOutcome())
                .ninOutcome(ninResult.map(NinResult::getOutcome).orElse(null))
                .faceMatchPassed(livenessResult.getFaceMatchPassed())
                .livenessPassed(livenessResult.getLivenessPassed())
                .bankStatementOutcome(verificationCase.getBankStatementOutcome())
                .allowMismatch(true)
                .build());
    if (trustTier == null) {
      throw new UnprocessableEntityException(
          "VERIFICATION_CASE_NOT_APPROVABLE", "Minimum Tier 1 requirements (BVN and face match) are not met");
    }

    verificationCase.setStatus(VerificationStatus.VERIFIED);
    verificationCase.setTrustTier(trustTier);
    verificationCase.setRejectionReason(null);
    verificationCase.setNeedsMoreInfoReason(null);

    outboxEventWriter.write(
        "VerificationCase",
        verificationCase.getId().toString(),
        KafkaTopics.KYC_VERIFIED,
        KycVerifiedPayload.builder()
            .verificationCaseId(verificationCase.getId())
            .userId(verificationCase.getUserId())
            .trustTier(trustTier.name())
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());
  }

  private void reject(VerificationCase verificationCase, ReviewDecisionRequest request, Instant now) {
    String reason = requireReason(request);
    verificationCase.setStatus(VerificationStatus.REJECTED);
    verificationCase.setRejectionReason(reason);
    verificationCase.setTrustTier(null);

    outboxEventWriter.write(
        "VerificationCase",
        verificationCase.getId().toString(),
        KafkaTopics.KYC_REJECTED,
        KycRejectedPayload.builder()
            .verificationCaseId(verificationCase.getId())
            .userId(verificationCase.getUserId())
            .reason(reason)
            .occurredAtEpochMillis(now.toEpochMilli())
            .build());
  }

  private void requestInfo(VerificationCase verificationCase, ReviewDecisionRequest request) {
    String reason = requireReason(request);
    verificationCase.setStatus(VerificationStatus.NEEDS_MORE_INFO);
    verificationCase.setNeedsMoreInfoReason(reason);
    // No outbox event: per the PRD's event catalogue, kyc.verified/kyc.rejected are the only two
    // KYC events — NEEDS_MORE_INFO is not a verdict the rest of the platform needs to react to.
  }

  private String requireReason(ReviewDecisionRequest request) {
    if (request.getReason() == null || request.getReason().isBlank()) {
      throw new BadRequestException("REVIEW_REASON_REQUIRED", "A reason is required for this decision");
    }
    return request.getReason();
  }
}
