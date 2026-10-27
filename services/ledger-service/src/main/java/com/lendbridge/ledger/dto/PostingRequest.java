package com.lendbridge.ledger.dto;

import com.lendbridge.ledger.model.DebitCredit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostingRequest {

  @NotNull private UUID accountId;
  @NotNull private DebitCredit direction;
  @Positive private long amountKobo;
}
