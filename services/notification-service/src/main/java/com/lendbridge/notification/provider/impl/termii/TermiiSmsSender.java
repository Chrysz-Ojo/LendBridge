package com.lendbridge.notification.provider.impl.termii;

import com.lendbridge.notification.provider.SmsSender;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "termii")
public class TermiiSmsSender implements SmsSender {
  private final RestClient client;
  private final String apiKey;
  private final String senderId;

  public TermiiSmsSender(
      @Value("${notification.termii.base-url}") String baseUrl,
      @Value("${notification.termii.api-key}") String apiKey,
      @Value("${notification.sms.sender-id}") String senderId) {
    this.client = RestClient.builder().baseUrl(baseUrl).build();
    this.apiKey = apiKey;
    this.senderId = senderId;
  }

  @Override
  public void send(String phone, String message) {
    client
        .post()
        .uri("/api/sms/send")
        .body(
            Map.of(
                "api_key", apiKey,
                "to", phone.replace("+", ""),
                "from", senderId,
                "sms", message,
                "type", "plain",
                "channel", "dnd"))
        .retrieve()
        .toBodilessEntity();
  }
}
