package com.lendbridge.kyc.dto;

import com.lendbridge.kyc.model.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code reason} is required for {@code REJECT}/{@code REQUEST_INFO} (enforced in
 * {@code ReviewServiceImpl}, not bean validation, since it's conditional on {@code decision}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDecisionRequest {
  @NotNull private ReviewDecision decision;
  private String reason;
}
