package com.lendbridge.notification.provider.impl.africastalking;

import com.lendbridge.notification.provider.SmsSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Africa's Talking SMS API (https://developers.africastalking.com/docs/sms/sending): a form-
 * urlencoded POST to {@code /version1/messaging} with the API key in a header rather than the
 * body (unlike Termii's). */
@Component
@ConditionalOnProperty(name = "notification.sms.provider", havingValue = "africastalking")
public class AfricasTalkingSmsSender implements SmsSender {
  private final RestClient client;
  private final String username;
  private final String senderId;

  public AfricasTalkingSmsSender(
      @Value("${notification.africastalking.base-url}") String baseUrl,
      @Value("${notification.africastalking.api-key}") String apiKey,
      @Value("${notification.africastalking.username}") String username,
      @Value("${notification.sms.sender-id}") String senderId) {
    this.client =
        RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("apiKey", apiKey)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build();
    this.username = username;
    this.senderId = senderId;
  }

  @Override
  public void send(String phone, String message) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("username", username);
    form.add("to", phone);
    form.add("message", message);
    form.add("from", senderId);

    client
        .post()
        .uri("/version1/messaging")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .toBodilessEntity();
  }

}
