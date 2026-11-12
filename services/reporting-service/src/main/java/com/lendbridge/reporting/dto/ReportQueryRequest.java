package com.lendbridge.reporting.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code from}/{@code to} are both optional — null means no constraint on that side of the
 * range, same convention as audit-service's query API. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportQueryRequest {
  private Instant from;
  private Instant to;
}
