package com.lendbridge.adminbff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationRunRequest {
  @NotBlank private String provider;
  @NotNull private Instant periodStart;
  @NotNull private Instant periodEnd;
}
