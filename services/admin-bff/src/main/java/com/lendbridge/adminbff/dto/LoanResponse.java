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
public class LoanResponse {
  private UUID id;
  private UUID borrowerUserId;
  private long amountKobo;
  private int tenorMonths;
  private String purpose;
  private String purposeDetail;
  private int interestRateBps;
  private String status;
  private boolean reviewRequired;
  private String rejectionReason;
  private Instant createdAt;
  private Instant updatedAt;
}
