package com.lendbridge.payment.wallet;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mirrors wallet-service's own {@code BankAccountPayoutDetailsResponse}. {@code verificationStatus}
 * is a plain string, not a shared enum type, to avoid coupling to wallet-service's own enum. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountPayoutDetails {
  private UUID id;
  private String bankCode;
  private String bankName;
  private String accountNumber;
  private String accountHolderName;
  private String verificationStatus;
}
