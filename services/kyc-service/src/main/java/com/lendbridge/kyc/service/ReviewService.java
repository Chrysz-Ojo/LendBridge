package com.lendbridge.kyc.service;

import com.lendbridge.kyc.dto.ReviewDecisionRequest;
import com.lendbridge.kyc.dto.ReviewQueueItemResponse;
import com.lendbridge.kyc.dto.VerificationStatusResponse;
import java.util.List;
import java.util.UUID;

public interface ReviewService {

  List<ReviewQueueItemResponse> getQueue();

  VerificationStatusResponse decide(UUID caseId, ReviewDecisionRequest request, UUID officerUserId);
}
