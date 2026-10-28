package com.lendbridge.kyc.dto;

import com.lendbridge.kyc.model.CheckOutcome;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of PRD E5-S3's "verification badges show which checks passed (BVN, ID, liveness, bank
 * statement)" — {@code checkName} is one of {@code BVN}, {@code NIN}, {@code ID}, {@code LIVENESS},
 * {@code BANK_STATEMENT}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckBadgeResponse {
  private String checkName;
  private CheckOutcome outcome;
  private boolean passed;
}
