package com.lendbridge.wallet.dto;

import com.lendbridge.wallet.model.BankVerificationStatus;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Unlike {@link BankAccountResponse} (which masks the account number for the end-user list
 * view), this carries the full, decrypted account number — only for payout construction, never
 * for display. Only ever returned to the resource owner's own forwarded JWT, same scoping as
 * every other wallet-service endpoint; the user already knows their own account number, since
 * they supplied it themselves. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountPayoutDetailsResponse {
  private UUID id;
  private String bankCode;
  private String bankName;
  private String accountNumber;
  private String accountHolderName;
  private BankVerificationStatus verificationStatus;
}
