package com.lendbridge.marketplace.dto;

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
public class ListingResponse {
  private UUID id;
  private long amountKobo;
  private int interestRateBps;
  private int tenorMonths;
  private String purpose;
  private long fundedAmountKobo;
  private Instant listedAt;
}
