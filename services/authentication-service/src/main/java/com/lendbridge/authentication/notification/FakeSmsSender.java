package com.lendbridge.authentication.notification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fake SMS adapter for local development and tests; see {@link FakeEmailSender}. */
@Component
public class FakeSmsSender implements SmsSender {

  private static final Logger log = LoggerFactory.getLogger(FakeSmsSender.class);

  private final List<SentSms> sent = new CopyOnWriteArrayList<>();

  @Override
  public void send(String toPhone, String message) {
    log.info("Fake SMS to {}", toPhone);
    sent.add(new SentSms(toPhone, message, Instant.now()));
  }

  public List<SentSms> sentMessages() {
    return Collections.unmodifiableList(sent);
  }

  public void clear() {
    sent.clear();
  }
}
