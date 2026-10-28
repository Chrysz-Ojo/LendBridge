package com.lendbridge.kyc.document;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Local copy of document-service's {@code GET /api/v1/documents/{id}} response shape — only the
 * fields {@link DocumentGateway} actually needs. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {
  private UUID id;
  private DocumentType documentType;
  private String originalFilename;
  private String contentType;
  private long sizeBytes;
  private String checksumSha256;
  private ScanStatus scanStatus;
  private Instant createdAt;
}
