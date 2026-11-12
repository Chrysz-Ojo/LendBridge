package com.lendbridge.messaging.model;

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

/** PRD E9-S5: "Report button flags the thread to the admin queue" — the admin queue is simply
 * every row here with {@code status = OPEN}, listable by the {@code SUPPORT} role. */
@Entity
@Table(name = "abuse_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AbuseReport {

  @Id private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(name = "reporter_user_id", nullable = false)
  private UUID reporterUserId;

  @Column(name = "reason", nullable = false, columnDefinition = "text")
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AbuseReportStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
