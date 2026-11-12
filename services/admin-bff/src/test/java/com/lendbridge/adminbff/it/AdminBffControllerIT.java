package com.lendbridge.adminbff.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.adminbff.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Role-gating across a representative subset of the 11 pass-through integrations — not all 11
 * need their own IT, same principle as the gateway unit tests covering only 2 of 11. */
class AdminBffControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority KYC_OFFICER_AUTHORITY = new SimpleGrantedAuthority("ROLE_KYC_OFFICER");
  private static final GrantedAuthority SUPER_ADMIN_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPER_ADMIN");

  @Test
  void kycReviewQueue_kycOfficerCanRead_otherRolesForbidden() throws Exception {
    KYC_WIREMOCK.resetAll();
    stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v1/kyc/review/queue"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"success":true,"data":[],"message":null,"timestamp":"2026-01-01T00:00:00Z"}
                        """)));

    mockMvc
        .perform(
            get("/api/v1/admin/kyc/review/queue")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("KYC_OFFICER")))
                        .authorities(KYC_OFFICER_AUTHORITY)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()", org.hamcrest.Matchers.equalTo(0)));

    mockMvc
        .perform(get("/api/v1/admin/kyc/review/queue").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void auditRecords_superAdminOnly() throws Exception {
    AUDIT_WIREMOCK.resetAll();
    stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v1/audit/records"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {"success":true,"data":[],"message":null,"timestamp":"2026-01-01T00:00:00Z"}
                        """)));

    mockMvc
        .perform(
            get("/api/v1/admin/audit/records")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPER_ADMIN")))
                        .authorities(SUPER_ADMIN_AUTHORITY)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/admin/audit/records")
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("KYC_OFFICER")))
                        .authorities(KYC_OFFICER_AUTHORITY)))
        .andExpect(status().isForbidden());
  }
}
