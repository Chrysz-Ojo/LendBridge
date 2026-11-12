package com.lendbridge.support.model;

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
 * Owned entirely by this service — {@code userId} is a plain column referencing
 * authentication-service's user id, never a cross-service foreign key. {@code attachmentDocumentId}
 * is likewise a bare {@code UUID} reference to a document-service upload that already exists (same
 * idiom as loan-service's {@code LoanApplication.agreementDocumentId}) — this service never calls
 * document-service live.
 */
@Entity
@Table(name = "ticket")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "subject", nullable = false)
  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private TicketCategory category;

  @Column(name = "category_detail")
  private String categoryDetail;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TicketStatus status;

  @Column(name = "attachment_document_id")
  private UUID attachmentDocumentId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
