package com.lendbridge.risk.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Decouples {@link com.lendbridge.risk.service.RiskAssessmentService} from the Avro-generated
 * {@code loan.submitted} record — the messaging layer maps one to the other. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanSubmittedEvent {
  private UUID loanId;
  private UUID borrowerUserId;
  private long amountKobo;
  private int tenorMonths;
  private int interestRateBps;
  private Instant occurredAt;
}
