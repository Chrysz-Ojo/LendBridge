package com.lendbridge.support.it;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.lendbridge.support.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class FaqControllerIT extends AbstractIntegrationTest {

  private static final GrantedAuthority SUPPORT_AUTHORITY = new SimpleGrantedAuthority("ROLE_SUPPORT");

  @Test
  void faqs_arePubliclyReadable_withNoAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/support/faqs")).andExpect(status().isOk());
  }

  @Test
  void adminCrud_isStaffOnly_andUnpublishedFaqsNeverAppearInPublicSearch() throws Exception {
    // non-staff cannot create
    mockMvc
        .perform(
            post("/api/v1/support/admin/faqs")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"category":"LOAN","question":"How fast is funding?","answer":"Under 72 hours."}
                    """))
        .andExpect(status().isForbidden());

    // staff creates a draft (unpublished) FAQ
    String created =
        mockMvc
            .perform(
                post("/api/v1/support/admin/faqs")
                    .with(
                        jwt()
                            .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                            .authorities(SUPPORT_AUTHORITY))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"category":"LOAN","question":"How fast is funding?","answer":"Under 72 hours.","published":false}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.published", equalTo(false)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String faqId = JsonPath.read(created, "$.data.id");

    mockMvc.perform(get("/api/v1/support/faqs?q=funding")).andExpect(jsonPath("$.data.length()", equalTo(0)));

    // staff publishes it via update
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/support/admin/faqs/" + faqId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"category":"LOAN","question":"How fast is funding?","answer":"Under 72 hours.","published":true}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/support/faqs?q=funding"))
        .andExpect(jsonPath("$.data.length()", equalTo(1)))
        .andExpect(jsonPath("$.data[0].question", equalTo("How fast is funding?")));

    // staff deletes it
    mockMvc
        .perform(
            delete("/api/v1/support/admin/faqs/" + faqId)
                .with(
                    jwt()
                        .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", List.of("SUPPORT")))
                        .authorities(SUPPORT_AUTHORITY)))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/support/faqs?q=funding")).andExpect(jsonPath("$.data.length()", equalTo(0)));
  }
}
