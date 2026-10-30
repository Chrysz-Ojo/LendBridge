package com.lendbridge.investment.wallet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A local copy of wallet-service's own {@code CreateHoldRequest} shape — same pattern as
 * payment-service's {@code RegisterVirtualAccountCommand}: no compile-time dependency on
 * wallet-service's module, just the agreed wire shape. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateHoldCommand {
  private long amountKobo;
  private String reference;
}
