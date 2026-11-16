package com.lendbridge.wallet.outbox;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletHoldsCaptureAssembledPayload {
  private String loanId;
  private boolean success;
  private List<String> holdIds;
  private String failureReason;
  private long occurredAtEpochMillis;
}
