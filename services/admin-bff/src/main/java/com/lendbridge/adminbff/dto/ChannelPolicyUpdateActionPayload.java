package com.lendbridge.adminbff.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The shape {@code ApprovalRequest.actionPayload} must hold for {@code CHANNEL_POLICY_UPDATE} —
 * {@code {"eventType":"...","emailEnabled":true,"smsEnabled":true,"inAppEnabled":true}} — parsed
 * by {@code ApprovalServiceImpl} on approve, never exposed on any controller endpoint directly.
 * Bundles the event type that the old direct endpoint took as a path variable, since a generic
 * actionPayload has no separate path-variable concept. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelPolicyUpdateActionPayload {
  private String eventType;
  private Boolean emailEnabled;
  private Boolean smsEnabled;
  private Boolean inAppEnabled;
}
