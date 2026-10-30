package com.lendbridge.kyc.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.kyc.dto.ReviewDecisionRequest;
import com.lendbridge.kyc.dto.ReviewQueueItemResponse;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.model.BvnResult;
import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.LivenessResult;
import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.model.ReviewDecision;
import com.lendbridge.kyc.model.TrustTier;
import com.lendbridge.kyc.model.VerificationCase;
import com.lendbridge.kyc.model.VerificationStatus;
import com.lendbridge.kyc.outbox.KafkaTopics;
import com.lendbridge.kyc.repository.BvnResultRepository;
import com.lendbridge.kyc.repository.LivenessResultRepository;
import com.lendbridge.kyc.repository.NinResultRepository;
import com.lendbridge.kyc.repository.VerificationCaseRepository;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.ConflictException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

  @Mock private VerificationCaseRepository verificationCaseRepository;
  @Mock private BvnResultRepository bvnResultRepository;
  @Mock private NinResultRepository ninResultRepository;
  @Mock private LivenessResultRepository livenessResultRepository;
  @Mock private VerificationResponseAssembler responseAssembler;
  @Mock private OutboxEventWriter outboxEventWriter;

  private ReviewServiceImpl service;
  private UUID caseId;
  private UUID officerId;
  private VerificationCase pendingCase;

  @BeforeEach
  void setUp() {
    service =
        new ReviewServiceImpl(
            verificationCaseRepository,
            bvnResultRepository,
            ninResultRepository,
            livenessResultRepository,
            responseAssembler,
            outboxEventWriter);

    caseId = UUID.randomUUID();
    officerId = UUID.randomUUID();
    pendingCase =
        VerificationCase.builder()
            .id(caseId)
            .userId(UUID.randomUUID())
            .status(VerificationStatus.PENDING)
            .reviewRequired(true)
            .idCardDocumentId(UUID.randomUUID())
            .selfieDocumentId(UUID.randomUUID())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

    org.mockito.Mockito.lenient()
        .when(responseAssembler.toStatusResponse(any(VerificationCase.class)))
        .thenAnswer(
            invocation -> {
              VerificationCase verificationCase = invocation.getArgument(0);
              return VerificationStatusResponse.builder()
                  .id(verificationCase.getId())
                  .status(verificationCase.getStatus())
                  .trustTier(verificationCase.getTrustTier())
                  .rejectionReason(verificationCase.getRejectionReason())
                  .needsMoreInfoReason(verificationCase.getNeedsMoreInfoReason())
                  .build();
            });
  }

  @Test
  void getQueue_returnsPendingReviewRequiredCases() {
    when(verificationCaseRepository.findAllByStatusAndReviewRequiredTrueOrderByCreatedAtAsc(VerificationStatus.PENDING))
        .thenReturn(List.of(pendingCase));
    when(responseAssembler.toQueueItem(pendingCase))
        .thenReturn(ReviewQueueItemResponse.builder().id(caseId).status(VerificationStatus.PENDING).build());

    List<ReviewQueueItemResponse> queue = service.getQueue();

    assertThat(queue).hasSize(1);
    assertThat(queue.get(0).getId()).isEqualTo(caseId);
  }

  @Test
  void decide_approve_verifiesTheCaseAndComputesTheTrustTier() {
    when(verificationCaseRepository.findById(caseId)).thenReturn(Optional.of(pendingCase));
    when(bvnResultRepository.findByVerificationCaseId(caseId))
        .thenReturn(Optional.of(BvnResult.builder().outcome(CheckOutcome.MISMATCH).provider(ProviderName.FAKE).build()));
    when(ninResultRepository.findByVerificationCaseId(caseId)).thenReturn(Optional.empty());
    when(livenessResultRepository.findByVerificationCaseId(caseId))
        .thenReturn(
            Optional.of(
                LivenessResult.builder()
                    .outcome(CheckOutcome.PASSED)
                    .faceMatchPassed(true)
                    .livenessPassed(true)
                    .provider(ProviderName.FAKE)
                    .build()));

    VerificationStatusResponse response = service.decide(caseId, new ReviewDecisionRequest(ReviewDecision.APPROVE, null), officerId);

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(response.getTrustTier()).isEqualTo(TrustTier.TIER_1);
    assertThat(pendingCase.getDecidedByUserId()).isEqualTo(officerId);
    assertThat(pendingCase.isReviewRequired()).isFalse();
    verify(outboxEventWriter).write(eq("VerificationCase"), anyString(), eq(KafkaTopics.KYC_VERIFIED), any());
  }

  @Test
  void decide_approve_rejectsWhenEvenAMismatchCannotReachTierOne() {
    when(verificationCaseRepository.findById(caseId)).thenReturn(Optional.of(pendingCase));
    when(bvnResultRepository.findByVerificationCaseId(caseId))
        .thenReturn(Optional.of(BvnResult.builder().outcome(CheckOutcome.PASSED).provider(ProviderName.FAKE).build()));
    when(ninResultRepository.findByVerificationCaseId(caseId)).thenReturn(Optional.empty());
    when(livenessResultRepository.findByVerificationCaseId(caseId))
        .thenReturn(
            Optional.of(
                LivenessResult.builder()
                    .outcome(CheckOutcome.FAILED)
                    .faceMatchPassed(false)
                    .livenessPassed(false)
                    .provider(ProviderName.FAKE)
                    .build()));

    assertThatThrownBy(() -> service.decide(caseId, new ReviewDecisionRequest(ReviewDecision.APPROVE, null), officerId))
        .isInstanceOf(UnprocessableEntityException.class);
  }

  @Test
  void decide_reject_requiresAReason() {
    when(verificationCaseRepository.findById(caseId)).thenReturn(Optional.of(pendingCase));

    assertThatThrownBy(() -> service.decide(caseId, new ReviewDecisionRequest(ReviewDecision.REJECT, null), officerId))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void decide_reject_setsRejectedAndPublishesKycRejected() {
    when(verificationCaseRepository.findById(caseId)).thenReturn(Optional.of(pendingCase));

    VerificationStatusResponse response =
        service.decide(caseId, new ReviewDecisionRequest(ReviewDecision.REJECT, "fraudulent documents"), officerId);

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(response.getRejectionReason()).isEqualTo("fraudulent documents");
    verify(outboxEventWriter).write(eq("VerificationCase"), anyString(), eq(KafkaTopics.KYC_REJECTED), any());
  }

  @Test
  void decide_requestInfo_setsNeedsMoreInfoWithoutPublishingAnEvent() {
    when(verificationCaseRepository.findById(caseId)).thenReturn(Optional.of(pendingCase));

    VerificationStatusResponse response =
        service.decide(caseId, new ReviewDecisionRequest(ReviewDecision.REQUEST_INFO, "please resubmit a clearer selfie"), officerId);

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.NEEDS_MORE_INFO);
    assertThat(response.getNeedsMoreInfoReason()).isEqualTo("please resubmit a clearer selfie");
    verify(outboxEventWriter, never()).write(anyString(), anyString(), anyString(), any());
  }

  @Test
  void decide_throwsConflict_whenCaseIsAlreadyDecided() {
    pendingCase.setStatus(VerificationStatus.VERIFIED);
    when(verificationCaseRepository.findById(caseId)).thenReturn(Optional.of(pendingCase));

    assertThatThrownBy(() -> service.decide(caseId, new ReviewDecisionRequest(ReviewDecision.APPROVE, null), officerId))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void decide_throwsNotFound_whenCaseDoesNotExist() {
    when(verificationCaseRepository.findById(caseId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.decide(caseId, new ReviewDecisionRequest(ReviewDecision.APPROVE, null), officerId))
        .isInstanceOf(NotFoundException.class);
  }
}
