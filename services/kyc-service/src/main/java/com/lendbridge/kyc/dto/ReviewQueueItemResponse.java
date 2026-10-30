package com.lendbridge.kyc.dto;

import com.lendbridge.kyc.model.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What a KYC officer sees in the review queue. {@code documentSnapshot} is the raw JSON metadata
 * snapshot (document id/type/scan-status/checksum) captured at check time —
 * {@code VerificationCase.documentSnapshot} — not a live re-fetch, since there is no
 * service-to-service auth yet for an officer's own JWT to read a borrower's documents (see
 * {@code DocumentFeignConfig}'s javadoc and ADR 0014).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewQueueItemResponse {
  private UUID id;
  private UUID userId;
  private VerificationStatus status;
  private List<CheckBadgeResponse> badges;
  private UUID idCardDocumentId;
  private UUID selfieDocumentId;
  private UUID bankStatementDocumentId;
  private String documentSnapshot;
  private Instant createdAt;
}
