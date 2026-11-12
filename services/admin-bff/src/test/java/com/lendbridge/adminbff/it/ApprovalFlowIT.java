package com.lendbridge.adminbff.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lendbridge.adminbff.avro.AdminAction;
import com.lendbridge.adminbff.support.AbstractIntegrationTest;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The core maker-checker invariant end to end: raise, reject-same-user, decide by a different
 * admin, assert the downstream role-assignment call actually happened and {@code
 * admin.action.approved} landed on the topic. */
class ApprovalFlowIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPER_ADMIN_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPER_ADMIN");

  @Test
  void raiseThenDecide_byDifferentAdmin_executesAndPublishes() throws Exception {
    UUID raisedBy = UUID.randomUUID();
    UUID decidedBy = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    KYC_WIREMOCK.resetAll();
    AUTH_WIREMOCK.resetAll();
    stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.post(urlPathMatching("/api/v1/auth/admin/users/.*/roles"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        """
                        {"success":true,"data":{"roles":["SUPPORT"]},"message":null,"timestamp":"2026-01-01T00:00:00Z"}
                        """)));

    String raisedJson =
        mockMvc
            .perform(
                post("/api/v1/admin/approvals")
                    .with(jwt().jwt(j -> j.subject(raisedBy.toString()).claim("roles", List.of("SUPER_ADMIN"))).authorities(SUPER_ADMIN_AUTHORITY))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"actionType":"ROLE_ASSIGNMENT","actionPayload":"{\\"targetUserId\\":\\"%s\\",\\"role\\":\\"SUPPORT\\"}"}
                        """
                            .formatted(targetUserId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("PENDING")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String approvalId = JsonPath.read(raisedJson, "$.data.id");

    // the raiser cannot also decide their own request
    mockMvc
        .perform(
            post("/api/v1/admin/approvals/" + approvalId + "/decide")
                .with(jwt().jwt(j -> j.subject(raisedBy.toString()).claim("roles", List.of("SUPER_ADMIN"))).authorities(SUPER_ADMIN_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"approve":true}
                    """))
        .andExpect(status().isConflict());

    // a different admin approves
    mockMvc
        .perform(
            post("/api/v1/admin/approvals/" + approvalId + "/decide")
                .with(jwt().jwt(j -> j.subject(decidedBy.toString()).claim("roles", List.of("SUPER_ADMIN"))).authorities(SUPER_ADMIN_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"approve":true,"reason":"confirmed with requester"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status", org.hamcrest.Matchers.equalTo("APPROVED")));

    verify(postRequestedFor(urlPathMatching("/api/v1/auth/admin/users/" + targetUserId + "/roles")));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(approvalRequestRepository.findById(UUID.fromString((String) approvalId)))
                    .hasValueSatisfying(a -> assertThat(a.getStatus().name()).isEqualTo("APPROVED")));

    assertAdminActionPublished("admin.action.approved", approvalId);
  }

  private void assertAdminActionPublished(String topic, String approvalId) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "approval-flow-it-" + UUID.randomUUID());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl());
    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);

    try (KafkaConsumer<String, AdminAction> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of(topic));
      long deadline = System.currentTimeMillis() + 10_000;
      while (System.currentTimeMillis() < deadline) {
        ConsumerRecords<String, AdminAction> records = consumer.poll(Duration.ofMillis(500));
        for (var record : records) {
          if (record.value().getApprovalId().toString().equals(approvalId)) {
            return;
          }
        }
      }
      throw new AssertionError("No " + topic + " record found for approval " + approvalId);
    }
  }
}
