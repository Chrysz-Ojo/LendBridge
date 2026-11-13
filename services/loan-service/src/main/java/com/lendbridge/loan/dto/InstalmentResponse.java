package com.lendbridge.loan.dto;

import com.lendbridge.loan.model.InstalmentStatus;
import java.time.Instant;
import java.time.LocalDate;
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
public class InstalmentResponse {
  private int instalmentNumber;
  private LocalDate dueDate;
  private long principalKobo;
  private long interestKobo;
  private long totalKobo;
  private long outstandingBalanceKobo;

  /** {@code PENDING} for a request-time preview (no real instalment exists yet) — only a
   * persisted, post-disbursement schedule carries a real {@link InstalmentStatus}. */
  @Builder.Default
  private InstalmentStatus status = InstalmentStatus.PENDING;

  private Instant paidAt;
}
