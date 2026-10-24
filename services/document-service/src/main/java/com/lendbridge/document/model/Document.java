package com.lendbridge.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Owned entirely by this service — {@code ownerId} is a plain column referencing the uploading
 * user's id (authentication-service), never a cross-service foreign key ("each service owns its
 * database; never read another service's tables"). {@code objectKey} is internal storage
 * addressing and is never exposed in API responses; callers only ever get a pre-signed download
 * URL.
 */
@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

  @Id private UUID id;

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", nullable = false)
  private DocumentType documentType;

  @Column(name = "original_filename", nullable = false)
  private String originalFilename;

  @Column(name = "content_type", nullable = false)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(name = "checksum_sha256", nullable = false)
  private String checksumSha256;

  @Column(name = "object_key", nullable = false)
  private String objectKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "scan_status", nullable = false)
  private ScanStatus scanStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
