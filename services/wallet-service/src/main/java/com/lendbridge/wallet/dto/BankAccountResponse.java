package com.lendbridge.wallet.dto;

import com.lendbridge.wallet.model.BankVerificationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Never carries the full account number — only a masked suffix, computed at response time. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccountResponse {
  private UUID id;
  private String bankCode;
  private String bankName;
  private String maskedAccountNumber;
  private String accountHolderName;
  private BankVerificationStatus verificationStatus;
  private Instant verifiedAt;
  private Instant createdAt;
}
