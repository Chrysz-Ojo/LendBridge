package com.lendbridge.loan.service;

import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.LoanReviewDecisionRequest;
import com.lendbridge.loan.dto.LoanReviewQueueItemResponse;
import java.util.List;
import java.util.UUID;

public interface LoanReviewService {

  List<LoanReviewQueueItemResponse> getQueue();

  LoanResponse decide(UUID loanId, LoanReviewDecisionRequest request, UUID officerUserId);
}
