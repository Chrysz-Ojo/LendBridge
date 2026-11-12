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

/** {@code ticketId} is a plain UUID column, not a JPA association (repo convention). Ids are
 * UUIDv7 (time-ordered), so {@code id > afterId} would be a correct chronological cursor the same
 * way messaging-service's {@code Message} already relies on — not needed yet since thread size is
 * small enough to return in full, but kept consistent for a future pagination need. */
@Entity
@Table(name = "ticket_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketMessage {

  @Id private UUID id;

  @Column(name = "ticket_id", nullable = false)
  private UUID ticketId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "author_role", nullable = false)
  private AuthorRole authorRole;

  @Column(name = "body", nullable = false)
  private String body;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
