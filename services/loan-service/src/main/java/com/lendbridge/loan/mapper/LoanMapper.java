package com.lendbridge.loan.mapper;

import com.lendbridge.loan.dto.LoanResponse;
import com.lendbridge.loan.dto.LoanReviewQueueItemResponse;
import com.lendbridge.loan.dto.ScheduleResponse;
import com.lendbridge.loan.model.LoanApplication;
import org.springframework.stereotype.Component;

/** Hand-written rather than MapStruct-generated — the mapping is a small, one-shot fixed set of
 * fields and {@code LoanResponse} needs a computed {@code schedule} folded in that isn't a direct
 * entity field, so a generated mapper would need a custom method anyway. Still its own top-level
 * package per CLAUDE.md's layering convention. */
@Component
public class LoanMapper {

  public LoanResponse toResponse(LoanApplication loan, ScheduleResponse schedule) {
    return LoanResponse.builder()
        .id(loan.getId())
        .borrowerUserId(loan.getBorrowerUserId())
        .amountKobo(loan.getAmountKobo())
        .tenorMonths(loan.getTenorMonths())
        .purpose(loan.getPurpose())
        .purposeDetail(loan.getPurposeDetail())
        .interestRateBps(loan.getInterestRateBps())
        .status(loan.getStatus())
        .reviewRequired(loan.isReviewRequired())
        .rejectionReason(loan.getRejectionReason())
        .agreementDocumentId(loan.getAgreementDocumentId())
        .listedAt(loan.getListedAt())
        .expiresAt(loan.getExpiresAt())
        .createdAt(loan.getCreatedAt())
        .updatedAt(loan.getUpdatedAt())
        .schedule(schedule)
        .build();
  }

  public LoanReviewQueueItemResponse toQueueItem(LoanApplication loan) {
    return LoanReviewQueueItemResponse.builder()
        .id(loan.getId())
        .borrowerUserId(loan.getBorrowerUserId())
        .amountKobo(loan.getAmountKobo())
        .tenorMonths(loan.getTenorMonths())
        .purpose(loan.getPurpose())
        .purposeDetail(loan.getPurposeDetail())
        .interestRateBps(loan.getInterestRateBps())
        .createdAt(loan.getCreatedAt())
        .build();
  }
}
