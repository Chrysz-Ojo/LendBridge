package com.lendbridge.payment.dto;

import com.lendbridge.payment.model.ProviderName;
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

  @NotNull private ProviderName provider;

  @NotNull private Instant periodStart;

  @NotNull private Instant periodEnd;
}
