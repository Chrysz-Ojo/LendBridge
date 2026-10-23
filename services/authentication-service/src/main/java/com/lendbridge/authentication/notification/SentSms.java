package com.lendbridge.authentication.notification;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SentSms {
  private final String toPhone;
  private final String message;
  private final Instant sentAt;
}
