package com.lendbridge.adminbff.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanReviewQueueItemResponse {
  private UUID id;
  private UUID borrowerUserId;
  private long amountKobo;
  private int tenorMonths;
  private String purpose;
  private String purposeDetail;
  private int interestRateBps;
  private Instant createdAt;
}
