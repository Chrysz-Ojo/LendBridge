package com.lendbridge.notification.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelPolicyRequest {
  @NotNull private Boolean emailEnabled;
  @NotNull private Boolean smsEnabled;
  @NotNull private Boolean inAppEnabled;
}
