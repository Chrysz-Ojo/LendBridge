package com.lendbridge.kyc.dto;

import com.lendbridge.kyc.model.TrustTier;
import com.lendbridge.kyc.model.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** PRD E2-S4: "Status: Pending, Verified, Rejected, Needs more info; trust tier 1-3 shown with the
 * borrowing or lending limit it unlocks; rejection reason shown." (the limit itself is computed by
 * Loan/Investment from {@code trustTier}, not duplicated here). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationStatusResponse {
  private UUID id;
  private VerificationStatus status;
  private TrustTier trustTier;
  private boolean reviewRequired;
  private String rejectionReason;
  private String needsMoreInfoReason;
  private List<CheckBadgeResponse> badges;
  private Instant createdAt;
  private Instant updatedAt;
}
