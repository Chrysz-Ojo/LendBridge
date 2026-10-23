package com.lendbridge.authentication.notification;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SentEmail {
  private final String toEmail;
  private final String subject;
  private final String body;
  private final Instant sentAt;
}
