package com.lendbridge.adminbff.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.lendbridge.adminbff.repository.ApprovalRequestRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres + Kafka + Schema Registry Testcontainers (same manually-started-static-block idiom as
 * every prior service's IT fixture this session), plus WireMock servers standing in for the
 * backend services admin-bff aggregates — none of the real 11 backends run alongside admin-bff in
 * a per-module test environment. Only the backends this service's ITs actually exercise are
 * stubbed (authentication-service for the maker-checker flow, kyc-service and audit-service as a
 * representative subset of the plain pass-through integrations) — same "representative coverage,
 * not all 11" principle as the gateway unit tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

  private static final String FIELD_ENCRYPTION_KEY = randomBase64Key();

  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("admin_bff_db")
          .withUsername("admin_bff")
          .withPassword("test-password");

  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))
          .withNetwork(Network.newNetwork())
          .withNetworkAliases("kafka");

  static final GenericContainer<?> SCHEMA_REGISTRY =
      new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.7.1"))
          .withNetwork(KAFKA.getNetwork())
          .withNetworkAliases("schema-registry")
          .withExposedPorts(8081)
          .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
          .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
          .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
          .dependsOn(KAFKA);

  protected static final WireMockServer AUTH_WIREMOCK =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
  protected static final WireMockServer KYC_WIREMOCK =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
  protected static final WireMockServer AUDIT_WIREMOCK =
      new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

  static {
    POSTGRES.start();
    KAFKA.start();
    SCHEMA_REGISTRY.start();
    AUTH_WIREMOCK.start();
    KYC_WIREMOCK.start();
    AUDIT_WIREMOCK.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add(
        "spring.kafka.producer.properties.schema.registry.url",
        () -> "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081));

    registry.add("platform.field-encryption.key", () -> FIELD_ENCRYPTION_KEY);

    registry.add("AUTH_SERVICE_URI", AUTH_WIREMOCK::baseUrl);
    registry.add("KYC_SERVICE_URI", KYC_WIREMOCK::baseUrl);
    registry.add("AUDIT_SERVICE_URI", AUDIT_WIREMOCK::baseUrl);
  }

  public static String kafkaBootstrapServers() {
    return KAFKA.getBootstrapServers();
  }

  public static String schemaRegistryUrl() {
    return "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081);
  }

  private static String randomBase64Key() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ApprovalRequestRepository approvalRequestRepository;
}
