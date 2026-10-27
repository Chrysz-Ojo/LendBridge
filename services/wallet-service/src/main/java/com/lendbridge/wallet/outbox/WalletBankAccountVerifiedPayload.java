package com.lendbridge.wallet.outbox;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** JSON shape stored in the outbox row; mirrors the fields of the
 * wallet_bank_account_verified.avsc schema. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletBankAccountVerifiedPayload {
  private String bankAccountId;
  private String walletId;
  private String userId;
  private long verifiedAtEpochMillis;
}
