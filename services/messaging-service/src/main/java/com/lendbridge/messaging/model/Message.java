package com.lendbridge.messaging.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * {@code conversationId} is a plain UUID column, not a JPA association, matching the rest of the
 * repo's convention for cross-entity references. {@code body} is already masked (see {@link
 * com.lendbridge.messaging.masking.ContactInfoMasker}) before it's ever persisted here — raw
 * contact info is never stored. {@code id} is a UUIDv7, so {@code id > :afterId} is a correct
 * chronological cursor for pagination without a separate timestamp column.
 */
@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

  @Id private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "sender_user_id", nullable = false)
  private UUID senderUserId;

  @Column(name = "body", nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
