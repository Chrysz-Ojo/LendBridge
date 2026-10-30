package com.lendbridge.loan.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A preview, not a persisted disbursement-anchored schedule — there is no real disbursement date
 * until the funding saga exists (docs/adr/0015-loan-service-phase-1-scope.md), so this is
 * recomputed on demand from the loan's stored amount/rate/tenor, anchored at the loan's own
 * creation date. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResponse {
  private long totalPrincipalKobo;
  private long totalInterestKobo;
  private long totalRepaymentKobo;
  private List<InstalmentResponse> instalments;
}
