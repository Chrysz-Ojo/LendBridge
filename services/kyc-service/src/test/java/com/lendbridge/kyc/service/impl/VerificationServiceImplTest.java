package com.lendbridge.kyc.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.kyc.crypto.IdentityHasher;
import com.lendbridge.kyc.document.DocumentGateway;
import com.lendbridge.kyc.document.DocumentResponse;
import com.lendbridge.kyc.document.DocumentType;
import com.lendbridge.kyc.document.ScanStatus;
import com.lendbridge.kyc.dto.StartVerificationRequest;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.ProviderName;
import com.lendbridge.kyc.model.TrustTier;
import com.lendbridge.kyc.model.VerificationCase;
import com.lendbridge.kyc.model.VerificationStatus;
import com.lendbridge.kyc.outbox.KafkaTopics;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import com.lendbridge.kyc.repository.BvnResultRepository;
import com.lendbridge.kyc.repository.LivenessResultRepository;
import com.lendbridge.kyc.repository.NinResultRepository;
import com.lendbridge.kyc.repository.VerificationCaseRepository;
import com.lendbridge.kyc.routing.RoutedCheckResult;
import com.lendbridge.kyc.routing.VerificationProviderRouter;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

  @Mock private VerificationCaseRepository verificationCaseRepository;
  @Mock private BvnResultRepository bvnResultRepository;
  @Mock private NinResultRepository ninResultRepository;
  @Mock private LivenessResultRepository livenessResultRepository;
  @Mock private VerificationProviderRouter router;
  @Mock private DocumentGateway documentGateway;
  @Mock private IdentityHasher identityHasher;
  @Mock private VerificationResponseAssembler responseAssembler;
  @Mock private OutboxEventWriter outboxEventWriter;

  private VerificationServiceImpl service;

  private UUID userId;
  private UUID idCardDocId;
  private UUID selfieDocId;
  private UUID bankStatementDocId;

  @BeforeEach
  void setUp() {
    service =
        new VerificationServiceImpl(
            verificationCaseRepository,
            bvnResultRepository,
            ninResultRepository,
            livenessResultRepository,
            router,
            documentGateway,
            identityHasher,
            responseAssembler,
            outboxEventWriter,
            new IdempotencyOperations());

    userId = UUID.randomUUID();
    idCardDocId = UUID.randomUUID();
    selfieDocId = UUID.randomUUID();
    bankStatementDocId = UUID.randomUUID();

    org.mockito.Mockito.lenient().when(identityHasher.hash(anyString())).thenReturn("hashed");
    org.mockito.Mockito.lenient()
        .when(responseAssembler.toStatusResponse(any(VerificationCase.class)))
        .thenAnswer(
            invocation -> {
              VerificationCase verificationCase = invocation.getArgument(0);
              return VerificationStatusResponse.builder()
                  .id(verificationCase.getId())
                  .status(verificationCase.getStatus())
                  .trustTier(verificationCase.getTrustTier())
                  .reviewRequired(verificationCase.isReviewRequired())
                  .rejectionReason(verificationCase.getRejectionReason())
                  .build();
            });
  }

  private StartVerificationRequest request(String bvn, String nin, boolean withBankStatement) {
    return new StartVerificationRequest(
        bvn, nin, "Jane Doe", LocalDate.of(1990, 1, 1), idCardDocId, selfieDocId, withBankStatement ? bankStatementDocId : null);
  }

  private void stubDocuments(boolean withBankStatement) {
    when(verificationCaseRepository.findOpenCaseByUserId(userId)).thenReturn(Optional.empty());
    when(documentGateway.fetchAndValidate(idCardDocId, DocumentType.ID_CARD))
        .thenReturn(documentResponse(idCardDocId, DocumentType.ID_CARD, "id.jpg"));
    when(documentGateway.fetchAndValidate(selfieDocId, DocumentType.SELFIE))
        .thenReturn(documentResponse(selfieDocId, DocumentType.SELFIE, "selfie.jpg"));
    if (withBankStatement) {
      when(documentGateway.fetchAndValidate(bankStatementDocId, DocumentType.BANK_STATEMENT))
          .thenReturn(documentResponse(bankStatementDocId, DocumentType.BANK_STATEMENT, "statement.pdf"));
    }
  }

  private DocumentResponse documentResponse(UUID id, DocumentType type, String filename) {
    return DocumentResponse.builder()
        .id(id)
        .documentType(type)
        .originalFilename(filename)
        .contentType("image/jpeg")
        .sizeBytes(1024)
        .checksumSha256("deadbeef")
        .scanStatus(ScanStatus.CLEAN)
        .createdAt(Instant.now())
        .build();
  }

  private void stubChecks(CheckOutcome bvnOutcome, CheckOutcome ninOutcome, CheckOutcome livenessOutcome) {
    when(router.checkBvn(any()))
        .thenReturn(
            new RoutedCheckResult(
                ProviderName.FAKE,
                ProviderCheckResult.builder()
                    .outcome(bvnOutcome)
                    .nameMatch(bvnOutcome == CheckOutcome.PASSED)
                    .dobMatch(bvnOutcome == CheckOutcome.PASSED)
                    .build()));
    if (ninOutcome != null) {
      when(router.checkNin(any()))
          .thenReturn(
              new RoutedCheckResult(
                  ProviderName.FAKE,
                  ProviderCheckResult.builder()
                      .outcome(ninOutcome)
                      .nameMatch(ninOutcome == CheckOutcome.PASSED)
                      .dobMatch(ninOutcome == CheckOutcome.PASSED)
                      .build()));
    }
    boolean livenessOk = livenessOutcome == CheckOutcome.PASSED;
    when(router.checkLiveness(any()))
        .thenReturn(
            new RoutedCheckResult(
                ProviderName.FAKE,
                ProviderCheckResult.builder()
                    .outcome(livenessOutcome)
                    .livenessScore(livenessOk ? 0.97 : 0.1)
                    .livenessPassed(livenessOk)
                    .faceMatchScore(livenessOk ? 0.96 : 0.1)
                    .faceMatchPassed(livenessOk)
                    .build()));
  }

  @Test
  void startVerification_tier1_bvnAndFaceMatchOnly() {
    stubDocuments(false);
    stubChecks(CheckOutcome.PASSED, null, CheckOutcome.PASSED);

    VerificationStatusResponse response = service.startVerification(userId, request("12345678901", null, false));

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(response.getTrustTier()).isEqualTo(TrustTier.TIER_1);
    verify(outboxEventWriter).write(eq("VerificationCase"), anyString(), eq(KafkaTopics.KYC_VERIFIED), any());
  }

  @Test
  void startVerification_tier2_addsNin() {
    stubDocuments(false);
    stubChecks(CheckOutcome.PASSED, CheckOutcome.PASSED, CheckOutcome.PASSED);

    VerificationStatusResponse response =
        service.startVerification(userId, request("12345678901", "98765432109", false));

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(response.getTrustTier()).isEqualTo(TrustTier.TIER_2);
  }

  @Test
  void startVerification_tier3_addsBankStatement() {
    stubDocuments(true);
    stubChecks(CheckOutcome.PASSED, CheckOutcome.PASSED, CheckOutcome.PASSED);

    VerificationStatusResponse response =
        service.startVerification(userId, request("12345678901", "98765432109", true));

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.VERIFIED);
    assertThat(response.getTrustTier()).isEqualTo(TrustTier.TIER_3);
  }

  @Test
  void startVerification_anyMismatchWithNoFailure_staysPendingAndRequiresReview() {
    stubDocuments(false);
    stubChecks(CheckOutcome.MISMATCH, null, CheckOutcome.PASSED);

    VerificationStatusResponse response = service.startVerification(userId, request("12345678901", null, false));

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.PENDING);
    assertThat(response.isReviewRequired()).isTrue();
    assertThat(response.getTrustTier()).isNull();
    verify(outboxEventWriter, never()).write(anyString(), anyString(), anyString(), any());
  }

  @Test
  void startVerification_anyFailure_autoRejectsAndSkipsTheQueue() {
    stubDocuments(false);
    stubChecks(CheckOutcome.PASSED, null, CheckOutcome.FAILED);

    VerificationStatusResponse response = service.startVerification(userId, request("12345678901", null, false));

    assertThat(response.getStatus()).isEqualTo(VerificationStatus.REJECTED);
    assertThat(response.isReviewRequired()).isFalse();
    assertThat(response.getRejectionReason()).isNotBlank();
    verify(outboxEventWriter).write(eq("VerificationCase"), anyString(), eq(KafkaTopics.KYC_REJECTED), any());
  }

  @Test
  void startVerification_whenAnOpenCaseAlreadyExists_replaysItWithoutRecheckingProviders() {
    VerificationCase existing =
        VerificationCase.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .status(VerificationStatus.PENDING)
            .reviewRequired(true)
            .idCardDocumentId(idCardDocId)
            .selfieDocumentId(selfieDocId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(verificationCaseRepository.findOpenCaseByUserId(userId)).thenReturn(Optional.of(existing));

    VerificationStatusResponse response = service.startVerification(userId, request("12345678901", null, false));

    assertThat(response.getId()).isEqualTo(existing.getId());
    verify(documentGateway, never()).fetchAndValidate(any(), any());
    verify(router, never()).checkBvn(any());
    verify(verificationCaseRepository, never()).save(any());
  }

  @Test
  void getStatus_returnsTheMostRecentCase() {
    VerificationCase latest =
        VerificationCase.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .status(VerificationStatus.VERIFIED)
            .trustTier(TrustTier.TIER_1)
            .idCardDocumentId(idCardDocId)
            .selfieDocumentId(selfieDocId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    when(verificationCaseRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(latest));

    VerificationStatusResponse response = service.getStatus(userId);

    assertThat(response.getId()).isEqualTo(latest.getId());
    assertThat(response.getTrustTier()).isEqualTo(TrustTier.TIER_1);
  }

  @Test
  void getStatus_throwsNotFound_whenNoCaseExists() {
    when(verificationCaseRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getStatus(userId)).isInstanceOf(NotFoundException.class);
  }
}
