package com.lendbridge.adminbff.model;

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
 * The generic maker-checker record (PRD E11-S3): one admin raises an action, a different admin
 * decides. {@code actionPayload} is the JSON body needed to actually perform the action once
 * approved (e.g. {@code {"targetUserId":"...","role":"SUPER_ADMIN"}} for {@code ROLE_ASSIGNMENT}) —
 * stored as text, same idiom as {@code outbox_event.payload} elsewhere in the repo.
 */
@Entity
@Table(name = "approval_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false)
  private ApprovalActionType actionType;

  @Column(name = "action_payload", nullable = false)
  private String actionPayload;

  @Column(name = "raised_by_user_id", nullable = false)
  private UUID raisedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ApprovalStatus status;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decision_reason")
  private String decisionReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "decided_at")
  private Instant decidedAt;
}
