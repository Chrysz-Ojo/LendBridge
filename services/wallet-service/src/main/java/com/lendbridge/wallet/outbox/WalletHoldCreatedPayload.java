package com.lendbridge.wallet.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JSON shape stored in the outbox row; mirrors the fields of the wallet_hold_created.avsc schema.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletHoldCreatedPayload {
  private String holdId;
  private String walletId;
  private String userId;
  private long amountKobo;
  private String reference;
  private long createdAtEpochMillis;
}
