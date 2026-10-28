package com.lendbridge.kyc.service.impl;

import com.lendbridge.kyc.dto.CheckBadgeResponse;
import com.lendbridge.kyc.dto.ReviewQueueItemResponse;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.mapper.VerificationCaseMapper;
import com.lendbridge.kyc.model.BvnResult;
import com.lendbridge.kyc.model.CheckOutcome;
import com.lendbridge.kyc.model.LivenessResult;
import com.lendbridge.kyc.model.NinResult;
import com.lendbridge.kyc.model.VerificationCase;
import com.lendbridge.kyc.repository.BvnResultRepository;
import com.lendbridge.kyc.repository.LivenessResultRepository;
import com.lendbridge.kyc.repository.NinResultRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Shared between {@link VerificationServiceImpl} and {@link ReviewServiceImpl} so both assemble
 * the PRD E5-S3 verification badges (BVN, NIN, ID, LIVENESS, BANK_STATEMENT) the same way, cross-
 * referencing {@code VerificationCase} against its {@code BvnResult}/{@code NinResult}/
 * {@code LivenessResult} rows rather than duplicating that logic in two services — not part of the
 * approved plan's explicit file list, but an internal implementation detail of the two services it
 * is listed under.
 */
@Component
@RequiredArgsConstructor
class VerificationResponseAssembler {

  private static final String BANK_STATEMENT_BADGE = "BANK_STATEMENT";

  private final BvnResultRepository bvnResultRepository;
  private final NinResultRepository ninResultRepository;
  private final LivenessResultRepository livenessResultRepository;
  private final VerificationCaseMapper mapper;

  VerificationStatusResponse toStatusResponse(VerificationCase verificationCase) {
    VerificationStatusResponse response = mapper.toStatusResponse(verificationCase);
    response.setBadges(buildBadges(verificationCase));
    return response;
  }

  ReviewQueueItemResponse toQueueItem(VerificationCase verificationCase) {
    ReviewQueueItemResponse response = mapper.toQueueItem(verificationCase);
    response.setBadges(buildBadges(verificationCase));
    return response;
  }


  List<CheckBadgeResponse> buildBadges(VerificationCase verificationCase) {
    List<CheckBadgeResponse> badges = new ArrayList<>();
    Optional<BvnResult> bvnResult = bvnResultRepository.findByVerificationCaseId(verificationCase.getId());
    bvnResult.ifPresent(r -> badges.add(badge("BVN", r.getOutcome())));

    Optional<NinResult> ninResult = ninResultRepository.findByVerificationCaseId(verificationCase.getId());
    ninResult.ifPresent(r -> badges.add(badge("NIN", r.getOutcome())));

    Optional<LivenessResult> livenessResult =
        livenessResultRepository.findByVerificationCaseId(verificationCase.getId());
    livenessResult.ifPresent(
        r -> {
          badges.add(
              badge("ID", Boolean.TRUE.equals(r.getFaceMatchPassed()) ? CheckOutcome.PASSED : CheckOutcome.MISMATCH));
          badges.add(
              badge(
                  "LIVENESS",
                  Boolean.TRUE.equals(r.getLivenessPassed()) ? CheckOutcome.PASSED : CheckOutcome.MISMATCH));
        });

    if (verificationCase.getBankStatementOutcome() != null) {
      badges.add(badge(BANK_STATEMENT_BADGE, verificationCase.getBankStatementOutcome()));
    }
    return badges;
  }

  private CheckBadgeResponse badge(String checkName, CheckOutcome outcome) {
    return CheckBadgeResponse.builder()
        .checkName(checkName)
        .outcome(outcome)
        .passed(outcome == CheckOutcome.PASSED)
        .build();
  }
}
