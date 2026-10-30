package com.lendbridge.loan.dto;

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
}
