package com.lendbridge.document.dto;

import com.lendbridge.document.model.DocumentType;
import com.lendbridge.document.model.ScanStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Never carries the internal {@code objectKey} — the download-url endpoint is the only way to
 * reach the actual bytes. */
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
