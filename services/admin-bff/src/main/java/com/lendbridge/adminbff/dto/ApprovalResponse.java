package com.lendbridge.adminbff.dto;

import com.lendbridge.adminbff.model.ApprovalActionType;
import com.lendbridge.adminbff.model.ApprovalStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalResponse {
  private UUID id;
  private ApprovalActionType actionType;
  private String actionPayload;
  private UUID raisedByUserId;
  private ApprovalStatus status;
  private UUID decidedByUserId;
  private String decisionReason;
  private Instant createdAt;
  private Instant decidedAt;
}
