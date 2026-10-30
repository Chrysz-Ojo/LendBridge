package com.lendbridge.loan.dto;

import com.lendbridge.loan.model.LoanPurpose;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What a risk officer sees in the review queue. No supporting-document fields — borrower document
 * uploads for a loan application (PRD E6-S2) are out of Phase 1's scope (see
 * docs/adr/0015-loan-service-phase-1-scope.md). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanReviewQueueItemResponse {
  private UUID id;
  private UUID borrowerUserId;
  private long amountKobo;
  private int tenorMonths;
  private LoanPurpose purpose;
  private String purposeDetail;
  private int interestRateBps;
  private Instant createdAt;
}
