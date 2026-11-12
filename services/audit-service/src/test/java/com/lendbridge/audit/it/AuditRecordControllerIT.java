package com.lendbridge.audit.it;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.audit.model.AuditRecord;
import com.lendbridge.audit.support.AbstractIntegrationTest;
import com.lendbridge.starter.crypto.UuidV7;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AuditRecordControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPER_ADMIN_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPER_ADMIN");

  @Test
  void query_withoutSuperAdminRole_isForbidden() throws Exception {
    mockMvc
        .perform(get("/api/v1/audit/records").with(jwt()))
        .andExpect(status().isForbidden());
  }

  @Test
  void query_withSuperAdminRole_returnsMatchingRecords() throws Exception {
    AuditRecord match =
        seedRecord("loan.submitted", "loan-abc", Instant.parse("2026-01-01T00:00:00Z"));
    seedRecord("kyc.verified", "user-xyz", Instant.parse("2026-01-02T00:00:00Z"));

    mockMvc
        .perform(
            get("/api/v1/audit/records")
                .param("topic", "loan.submitted")
                .with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(match.getId().toString()))
        .andExpect(jsonPath("$.data[0].aggregateKey").value("loan-abc"));
  }

  @Test
  void query_withNoMatchingFilter_returnsEmptyList() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/audit/records")
                .param("topic", "nonexistent.topic")
                .with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void query_filtersByTimeRange() throws Exception {
    seedRecord("payment.succeeded", "payment-1", Instant.parse("2020-01-01T00:00:00Z"));
    AuditRecord inRange = seedRecord("payment.succeeded", "payment-2", Instant.parse("2026-06-01T00:00:00Z"));

    mockMvc
        .perform(
            get("/api/v1/audit/records")
                .param("topic", "payment.succeeded")
                .param("from", "2025-01-01T00:00:00Z")
                .with(jwt().authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(inRange.getId().toString()));
  }

  private AuditRecord seedRecord(String topic, String aggregateKey, Instant occurredAt) {
    return auditRecordRepository.save(
        AuditRecord.builder()
            .id(UuidV7.generate())
            .topic(topic)
            .aggregateKey(aggregateKey)
            .payload("{}")
            .occurredAt(occurredAt)
            .recordedAt(Instant.now())
            .kafkaPartition(0)
            .kafkaOffset(0L)
            .build());
  }
}
