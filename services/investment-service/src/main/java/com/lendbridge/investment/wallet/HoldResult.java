package com.lendbridge.investment.wallet;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A local copy of wallet-service's own {@code HoldResponse} shape — only the fields this
 * service actually needs (same reasoning as loan-service's {@code KycStatusResponse}). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HoldResult {
  private UUID id;
}
