package com.lendbridge.marketplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** All filters are optional (PRD E3-S2: "filter and sort by each"). Assembled by the controller
 * from query parameters, per CLAUDE.md's request-object-as-service-parameter convention. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingSearchRequest {
  private Long minAmountKobo;
  private Long maxAmountKobo;
  private Integer minRateBps;
  private Integer maxRateBps;
  private Integer minTenorMonths;
  private Integer maxTenorMonths;
  private String purpose;
}
