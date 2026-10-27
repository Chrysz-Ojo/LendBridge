package com.lendbridge.ledger.dto;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** {@code balanceKobo} is always computed from postings at read time — never cached here. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceResponse {
  private UUID accountId;
  private String currency;
  private long balanceKobo;
}
