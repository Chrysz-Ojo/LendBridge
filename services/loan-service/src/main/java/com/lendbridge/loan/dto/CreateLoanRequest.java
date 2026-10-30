package com.lendbridge.loan.dto;

import com.lendbridge.loan.model.LoanPurpose;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tenor bounds and rate floor/cap are config-driven (env-overridable), so they're enforced in
 * {@code LoanServiceImpl}, not as bean-validation annotations here — same reasoning as
 * {@code ReviewDecisionRequest.reason} being conditionally required in the service layer. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateLoanRequest {
  @NotNull @Positive private Long amountKobo;
  @NotNull private Integer tenorMonths;
  @NotNull private LoanPurpose purpose;
  private String purposeDetail;
  @NotNull private Integer interestRateBps;
}
