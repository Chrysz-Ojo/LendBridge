package com.lendbridge.notification.provider.impl.fake;

import com.lendbridge.notification.provider.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "fake", matchIfMissing = true)
public class FakeSmsSender implements SmsSender {
  private static final Logger log = LoggerFactory.getLogger(FakeSmsSender.class);

  @Override
  public void send(String phone, String message) {
    String lastFour = phone.length() > 4 ? phone.substring(phone.length() - 4) : phone;
    log.info("FAKE SMS to=***{}", lastFour);
  }
}
