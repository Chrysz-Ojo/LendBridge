package com.lendbridge.wallet.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JSON shape stored in the outbox row; mirrors the fields of the wallet_created.avsc schema. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreatedPayload {
  private String walletId;
  private String userId;
  private String currency;
  private long createdAtEpochMillis;
}
