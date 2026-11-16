package com.lendbridge.loan.dto;

import com.lendbridge.loan.model.RepaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RepaymentRequest {
  @NotNull private RepaymentMethod method;
}
