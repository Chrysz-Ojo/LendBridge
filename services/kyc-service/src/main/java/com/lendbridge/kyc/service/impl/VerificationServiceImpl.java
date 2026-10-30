package com.lendbridge.kyc.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lendbridge.kyc.crypto.IdentityHasher;
import com.lendbridge.kyc.document.DocumentGateway;
import com.lendbridge.kyc.document.DocumentResponse;
import com.lendbridge.kyc.document.DocumentType;
import com.lendbridge.kyc.dto.StartVerificationRequest;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.model.BvnResult;
import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.LivenessResult;
import com.lendbridge.kyc.model.NinResult;
import com.lendbridge.kyc.model.TrustTier;
import com.lendbridge.kyc.model.VerificationCase;
import com.lendbridge.kyc.model.VerificationStatus;
import com.lendbridge.kyc.outbox.KafkaTopics;
import com.lendbridge.kyc.outbox.KycRejectedPayload;
import com.lendbridge.kyc.outbox.KycVerifiedPayload;
import com.lendbridge.kyc.provider.BvnCheckRequest;
import com.lendbridge.kyc.provider.LivenessCheckRequest;
import com.lendbridge.kyc.provider.NinCheckRequest;
import com.lendbridge.kyc.provider.ProviderCheckResult;
import com.lendbridge.kyc.repository.BvnResultRepository;
import com.lendbridge.kyc.repository.LivenessResultRepository;
import com.lendbridge.kyc.repository.NinResultRepository;
import com.lendbridge.kyc.repository.VerificationCaseRepository;
import com.lendbridge.kyc.routing.RoutedCheckResult;
import com.lendbridge.kyc.routing.VerificationProviderRouter;
import com.lendbridge.kyc.service.VerificationService;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.idempotency.IdempotencyOperations;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the approved plan's outcome-routing rules exactly:
 *
 * <ol>
 *   <li>All checks needed for &ge; Tier 1 {@code PASSED} &rarr; auto-{@code VERIFIED}, tier
 *       computed from whatever else also passed.
 *   <li>Any {@code MISMATCH}, no {@code FAILED} &rarr; stays {@code PENDING},
 *       {@code reviewRequired=true}; {@code NEEDS_MORE_INFO} is never set automatically.
 *   <li>Any {@code FAILED} &rarr; auto-{@code REJECTED} with a system reason, skipping the queue.
 *   <li>A transient failure (both providers down, document-service unreachable, a document not
 *       {@code CLEAN}/wrong type) is never recorded as a verdict — {@link DocumentGateway} and
 *       {@link VerificationProviderRouter} throw {@code DependencyUnavailableException}/
 *       {@code UnprocessableEntityException} instead, leaving the case untouched.
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {


  private final VerificationCaseRepository verificationCaseRepository;
  private final BvnResultRepository bvnResultRepository;
  private final NinResultRepository ninResultRepository;
  private final LivenessResultRepository livenessResultRepository;
  private final VerificationProviderRouter router;
  private final DocumentGateway documentGateway;
  private final IdentityHasher identityHasher;
  private final VerificationResponseAssembler responseAssembler;
  private final OutboxEventWriter outboxEventWriter;
  private final IdempotencyOperations idempotencyOperations;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  @Transactional
  public VerificationStatusResponse startVerification(UUID userId, StartVerificationRequest request) {
    return idempotencyOperations.runIdempotently(
        () -> verificationCaseRepository.findOpenCaseByUserId(userId).map(responseAssembler::toStatusResponse),
        () -> processNewVerification(userId, request));
  }

  private VerificationStatusResponse processNewVerification(UUID userId, StartVerificationRequest request) {
    DocumentResponse idCard = documentGateway.fetchAndValidate(request.getIdCardDocumentId(), DocumentType.ID_CARD);
    DocumentResponse selfie = documentGateway.fetchAndValidate(request.getSelfieDocumentId(), DocumentType.SELFIE);
    DocumentResponse bankStatement =
        request.getBankStatementDocumentId() == null
            ? null
            : documentGateway.fetchAndValidate(request.getBankStatementDocumentId(), DocumentType.BANK_STATEMENT);

    RoutedCheckResult bvnRouted =
        router.checkBvn(new BvnCheckRequest(request.getBvn(), request.getFullName(), request.getDateOfBirth()));
    ProviderCheckResult bvnCheck = bvnRouted.getResult();

    boolean ninAttempted = request.getNin() != null && !request.getNin().isBlank();
    RoutedCheckResult ninRouted =
        ninAttempted
            ? router.checkNin(new NinCheckRequest(request.getNin(), request.getFullName(), request.getDateOfBirth()))
            : null;
    ProviderCheckResult ninCheck = ninRouted == null ? null : ninRouted.getResult();

    RoutedCheckResult livenessRouted =
        router.checkLiveness(
            new LivenessCheckRequest(
                selfie.getId(), selfie.getOriginalFilename(), idCard.getId(), idCard.getOriginalFilename()));
    ProviderCheckResult livenessCheck = livenessRouted.getResult();

    // Bank-statement verification is a document-service rules check per the approved plan (clean
    // scan + correct type, both already enforced by fetchAndValidate above). The "dated within the
    // last 3 months" criterion from PRD E2-S3 would need document-service to expose a statement
    // date, which its current API doesn't — tracked as a known gap rather than faked here.
    CheckOutcome bankStatementOutcome = bankStatement == null ? null : CheckOutcome.PASSED;

    boolean anyFailed =
        bvnCheck.getOutcome() == CheckOutcome.FAILED
            || (ninCheck != null && ninCheck.getOutcome() == CheckOutcome.FAILED)
            || livenessCheck.getOutcome() == CheckOutcome.FAILED;
    boolean anyMismatch =
        bvnCheck.getOutcome() == CheckOutcome.MISMATCH
            || (ninCheck != null && ninCheck.getOutcome() == CheckOutcome.MISMATCH)
            || livenessCheck.getOutcome() == CheckOutcome.MISMATCH;

    UUID caseId = UuidV7.generate();
    Instant now = Instant.now();

    VerificationStatus status;
    TrustTier trustTier = null;
    boolean reviewRequired = false;
    String rejectionReason = null;

    if (anyFailed) {
      status = VerificationStatus.REJECTED;
      rejectionReason = buildRejectionReason(bvnCheck, ninCheck, livenessCheck);
    } else if (anyMismatch) {
      status = VerificationStatus.PENDING;
      reviewRequired = true;
    } else {
      status = VerificationStatus.VERIFIED;
      trustTier =
          TrustTierCalculator.compute(
              TrustTierCalculationRequest.builder()
                  .bvnOutcome(bvnCheck.getOutcome())
                  .ninOutcome(ninCheck == null ? null : ninCheck.getOutcome())
                  .faceMatchPassed(livenessCheck.getFaceMatchPassed())
                  .livenessPassed(livenessCheck.getLivenessPassed())
                  .bankStatementOutcome(bankStatementOutcome)
                  .allowMismatch(false)
                  .build());
    }

    VerificationCase verificationCase =
        VerificationCase.builder()
            .id(caseId)
            .userId(userId)
            .status(status)
            .trustTier(trustTier)
            .reviewRequired(reviewRequired)
            .rejectionReason(rejectionReason)
            .idCardDocumentId(idCard.getId())
            .selfieDocumentId(selfie.getId())
            .bankStatementDocumentId(bankStatement == null ? null : bankStatement.getId())
            .bankStatementOutcome(bankStatementOutcome)
            .documentSnapshot(buildDocumentSnapshot(idCard, selfie, bankStatement))
            .createdAt(now)
            .updatedAt(now)
            .build();
    verificationCaseRepository.save(verificationCase);

    bvnResultRepository.save(
        BvnResult.builder()
            .id(UuidV7.generate())
            .verificationCaseId(caseId)
            .provider(bvnRouted.getProvider())
            .bvn(request.getBvn())
            .bvnHash(identityHasher.hash(request.getBvn()))
            .nameMatch(bvnCheck.getNameMatch())
            .dobMatch(bvnCheck.getDobMatch())
            .outcome(bvnCheck.getOutcome())
            .rawResponse(bvnCheck.getRawResponseJson())
            .checkedAt(now)
            .build());

    if (ninCheck != null) {
      ninResultRepository.save(
          NinResult.builder()
              .id(UuidV7.generate())
              .verificationCaseId(caseId)
              .provider(ninRouted.getProvider())
              .nin(request.getNin())
              .ninHash(identityHasher.hash(request.getNin()))
              .nameMatch(ninCheck.getNameMatch())
              .dobMatch(ninCheck.getDobMatch())
              .outcome(ninCheck.getOutcome())
              .rawResponse(ninCheck.getRawResponseJson())
              .checkedAt(now)
              .build());
    }

    livenessResultRepository.save(
        LivenessResult.builder()
            .id(UuidV7.generate())
            .verificationCaseId(caseId)
            .provider(livenessRouted.getProvider())
            .selfieDocumentId(selfie.getId())
            .idCardDocumentId(idCard.getId())
            .livenessScore(livenessCheck.getLivenessScore())
            .livenessPassed(livenessCheck.getLivenessPassed())
            .faceMatchScore(livenessCheck.getFaceMatchScore())
            .faceMatchPassed(livenessCheck.getFaceMatchPassed())
            .outcome(livenessCheck.getOutcome())
            .rawResponse(livenessCheck.getRawResponseJson())
            .checkedAt(now)
            .build());

    if (status == VerificationStatus.VERIFIED) {
      outboxEventWriter.write(
          "VerificationCase",
          caseId.toString(),
          KafkaTopics.KYC_VERIFIED,
          KycVerifiedPayload.builder()
              .verificationCaseId(caseId)
              .userId(userId)
              .trustTier(trustTier.name())
              .occurredAtEpochMillis(now.toEpochMilli())
              .build());
    } else if (status == VerificationStatus.REJECTED) {
      outboxEventWriter.write(
          "VerificationCase",
          caseId.toString(),
          KafkaTopics.KYC_REJECTED,
          KycRejectedPayload.builder()
              .verificationCaseId(caseId)
              .userId(userId)
              .reason(rejectionReason)
              .occurredAtEpochMillis(now.toEpochMilli())
              .build());
    }

    return responseAssembler.toStatusResponse(verificationCase);
  }

  @Override
  @Transactional(readOnly = true)
  public VerificationStatusResponse getStatus(UUID userId) {
    VerificationCase verificationCase =
        verificationCaseRepository
            .findFirstByUserIdOrderByCreatedAtDesc(userId)
            .orElseThrow(() -> new NotFoundException("VERIFICATION_CASE_NOT_FOUND", "No verification case found"));
    return responseAssembler.toStatusResponse(verificationCase);
  }

  private String buildRejectionReason(
      ProviderCheckResult bvnCheck, ProviderCheckResult ninCheck, ProviderCheckResult livenessCheck) {
    if (bvnCheck.getOutcome() == CheckOutcome.FAILED) {
      return "BVN check failed";
    }
    if (ninCheck != null && ninCheck.getOutcome() == CheckOutcome.FAILED) {
      return "NIN check failed";
    }
    return "Liveness/selfie check failed";
  }

  private String buildDocumentSnapshot(DocumentResponse idCard, DocumentResponse selfie, DocumentResponse bankStatement) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("idCard", documentSnapshotEntry(idCard));
    snapshot.put("selfie", documentSnapshotEntry(selfie));
    if (bankStatement != null) {
      snapshot.put("bankStatement", documentSnapshotEntry(bankStatement));
    }
    try {
      return objectMapper.writeValueAsString(snapshot);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to serialize document snapshot", e);
    }
  }

  private Map<String, Object> documentSnapshotEntry(DocumentResponse document) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("documentId", document.getId().toString());
    entry.put("documentType", document.getDocumentType().name());
    entry.put("scanStatus", document.getScanStatus().name());
    entry.put("checksumSha256", document.getChecksumSha256());
    entry.put("originalFilename", document.getOriginalFilename());
    return entry;
  }
}
