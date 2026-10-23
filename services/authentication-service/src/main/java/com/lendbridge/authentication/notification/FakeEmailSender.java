package com.lendbridge.authentication.notification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fake email adapter for local development and tests: logs the message and keeps it in memory so
 * integration tests can assert on it, instead of calling a real email vendor (CLAUDE.md's
 * external-provider rule).
 */
@Component
public class FakeEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(FakeEmailSender.class);

  private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

  @Override
  public void send(String toEmail, String subject, String body) {
    log.info("Fake email to {} subject='{}'", toEmail, subject);
    sent.add(new SentEmail(toEmail, subject, body, Instant.now()));
  }

  public List<SentEmail> sentEmails() {
    return Collections.unmodifiableList(sent);
  }

  public void clear() {
    sent.clear();
  }
}
