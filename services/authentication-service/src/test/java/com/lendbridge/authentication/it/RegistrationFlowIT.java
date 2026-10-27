package com.lendbridge.authentication.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.lendbridge.authentication.avro.UserRegistered;
import com.lendbridge.authentication.dto.RegisterResponse;
import com.lendbridge.authentication.notification.FakeEmailSender;
import com.lendbridge.authentication.notification.FakeSmsSender;
import com.lendbridge.authentication.outbox.KafkaTopics;
import com.lendbridge.starter.outbox.OutboxEventRepository;
import com.lendbridge.starter.outbox.OutboxStatus;
import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.authentication.support.AbstractIntegrationTest;
import com.lendbridge.authentication.model.UserStatus;
import com.lendbridge.authentication.repository.UserJpaRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class RegistrationFlowIT extends AbstractIntegrationTest {

  @Autowired private ObjectMapper objectMapper;
  @Autowired private FakeSmsSender fakeSmsSender;
  @Autowired private FakeEmailSender fakeEmailSender;
  @Autowired private UserJpaRepository userRepository;
  @Autowired private OutboxEventRepository outboxEventRepository;

  private static final Pattern CODE_PATTERN = Pattern.compile("code is (\\d{6})");
  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=(\\S+)");

  @Test
  void registerVerifyPhoneVerifyEmailActivatesUserAndPublishesEvent() throws Exception {
    String email = "jane." + UUID.randomUUID() + "@example.com";
    String phone = "+234" + (800_000_0000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L));

    Map<String, Object> registerRequest =
        Map.of(
            "fullName", "Jane Doe",
            "email", email,
            "phone", phone,
            "password", "Str0ng!Passw0rd",
            "acceptTerms", true,
            "acceptPrivacy", true);

    String registerBody =
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ApiResponse<RegisterResponse> wrapped =
        objectMapper.readValue(registerBody, new TypeReference<ApiResponse<RegisterResponse>>() {});
    RegisterResponse registered = wrapped.getData();
    assertThat(registered.getStatus()).isEqualTo("PENDING_VERIFICATION");

    String smsCode = extractCode(fakeSmsSender);
    mockMvc
        .perform(
            post("/api/v1/auth/register/verify-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        Map.of("userId", registered.getUserId().toString(), "code", smsCode))))
        .andExpect(status().isOk());

    String emailToken = extractToken(fakeEmailSender);
    mockMvc
        .perform(
            post("/api/v1/auth/register/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", emailToken))))
        .andExpect(status().isOk());

    var user = userRepository.findById(registered.getUserId()).orElseThrow();
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.isEmailVerified()).isTrue();
    assertThat(user.isPhoneVerified()).isTrue();

    awaitOutboxPublished(registered.getUserId());
    assertKafkaMessagePublished(registered.getUserId(), email);
  }

  private String extractCode(FakeSmsSender sender) {
    Matcher matcher = CODE_PATTERN.matcher(sender.sentMessages().get(sender.sentMessages().size() - 1).getMessage());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private String extractToken(FakeEmailSender sender) {
    Matcher matcher = TOKEN_PATTERN.matcher(sender.sentEmails().get(sender.sentEmails().size() - 1).getBody());
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }

  private void awaitOutboxPublished(UUID userId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      boolean published =
          outboxEventRepository.findAll().stream()
              .anyMatch(
                  e ->
                      e.getAggregateId().equals(userId.toString())
                          && e.getStatus() == OutboxStatus.PUBLISHED);
      if (published) {
        return;
      }
      Thread.sleep(300);
    }
    throw new AssertionError("Outbox event for user " + userId + " was not published in time");
  }

  private void assertKafkaMessagePublished(UUID userId, String email) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        "io.confluent.kafka.serializers.KafkaAvroDeserializer");
    props.put("schema.registry.url", schemaRegistryUrl());
    props.put("specific.avro.reader", "true");
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    try (KafkaConsumer<String, UserRegistered> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(java.util.List.of(KafkaTopics.USER_REGISTERED));
      long deadline = System.currentTimeMillis() + 15_000;
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, UserRegistered> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, UserRegistered> record : records) {
          if (record.key().equals(userId.toString())) {
            assertThat(record.value().getUserId()).isEqualTo(userId.toString());
            assertThat(record.value().getEmail()).isEqualTo(email);
            assertThat(record.value().getEmailVerified()).isTrue();
            assertThat(record.value().getPhoneVerified()).isTrue();

            assertThat(headerValue(record, "ce_type")).isEqualTo(KafkaTopics.USER_REGISTERED);
            assertThat(headerValue(record, "ce_source")).isEqualTo("lendbridge://authentication-service");
            assertThat(headerValue(record, "ce_subject")).isEqualTo(userId.toString());
            assertThat(headerValue(record, "ce_id")).isNotBlank();
            assertThat(headerValue(record, "ce_time")).isNotBlank();
            return;
          }
        }
      }
    }
    throw new AssertionError("Did not receive user.registered Kafka message for " + userId);
  }

  private String headerValue(ConsumerRecord<String, UserRegistered> record, String key) {
    var header = record.headers().lastHeader(key);
    assertThat(header).isNotNull();
    return new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
  }
}
