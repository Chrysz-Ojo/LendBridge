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

/** PRD E9-S4: "searchable FAQ page managed by admins." {@code published} lets an admin draft one
 * before it's publicly visible; reuses {@link TicketCategory} rather than a second taxonomy. */
@Entity
@Table(name = "faq")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Faq {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false)
  private TicketCategory category;

  @Column(name = "question", nullable = false)
  private String question;

  @Column(name = "answer", nullable = false)
  private String answer;

  @Column(name = "published", nullable = false)
  private boolean published;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
