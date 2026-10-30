package com.lendbridge.kyc.mapper;

import com.lendbridge.kyc.dto.ReviewQueueItemResponse;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import com.lendbridge.kyc.model.VerificationCase;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** {@code badges} is cross-entity (built from {@code BvnResult}/{@code NinResult}/
 * {@code LivenessResult}, not a field on {@code VerificationCase} itself), so it's always ignored
 * here and filled in by the calling service after mapping — see {@code VerificationServiceImpl}. */
@Mapper(componentModel = "spring")
public interface VerificationCaseMapper {

  @Mapping(target = "badges", ignore = true)
  VerificationStatusResponse toStatusResponse(VerificationCase verificationCase);

  @Mapping(target = "badges", ignore = true)
  ReviewQueueItemResponse toQueueItem(VerificationCase verificationCase);
}
