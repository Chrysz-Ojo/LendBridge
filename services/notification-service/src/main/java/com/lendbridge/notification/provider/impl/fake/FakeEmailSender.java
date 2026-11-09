package com.lendbridge.notification.provider.impl.fake;

import com.lendbridge.notification.provider.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.email.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEmailSender implements EmailSender {
  private static final Logger log = LoggerFactory.getLogger(FakeEmailSender.class);

  @Override
  public void send(String to, String subject, String body) {
    log.info("FAKE EMAIL to={} subject='{}'", to, subject);
  }
}
