package com.lendbridge.ledger.dto;

import com.lendbridge.ledger.model.DebitCredit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OpenAccountRequest {

  @NotBlank private String accountType;
  @NotBlank private String currency;
  @NotNull private DebitCredit normalBalance;
  private String ownerReference;
}
