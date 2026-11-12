package com.lendbridge.adminbff.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Local mirror of kyc-service's {@code ReviewQueueItemResponse} — status is kept as a plain
 * {@code String} rather than a copied enum, same as every other read-only display field across
 * this service's local DTOs (see ADR 0023: admin-bff doesn't validate business values itself,
 * that's each backend's own job, so there's no need to copy every backend enum verbatim). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycReviewQueueItemResponse {
  private UUID id;
  private UUID userId;
  private String status;
  private UUID idCardDocumentId;
  private UUID selfieDocumentId;
  private UUID bankStatementDocumentId;
  private String documentSnapshot;
  private Instant createdAt;
}
