package com.lendbridge.loan.dto;

import com.lendbridge.loan.model.LoanPurpose;
import com.lendbridge.loan.model.LoanStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanResponse {
  private UUID id;
  private UUID borrowerUserId;
  private long amountKobo;
  private int tenorMonths;
  private LoanPurpose purpose;
  private String purposeDetail;
  private int interestRateBps;
  private LoanStatus status;
  private boolean reviewRequired;
  private String rejectionReason;
  private UUID agreementDocumentId;
  private Instant listedAt;
  private Instant expiresAt;
  private Instant createdAt;
  private Instant updatedAt;
  private ScheduleResponse schedule;
}
