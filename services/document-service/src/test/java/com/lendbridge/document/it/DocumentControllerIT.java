package com.lendbridge.document.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lendbridge.document.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentControllerIT extends AbstractIntegrationTest {

  @Test
  void upload_thenGet_thenDownloadUrl_roundTrips() throws Exception {
    UUID userId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "id-card.png", "image/png", new byte[] {1, 2, 3, 4});

    String uploadResponse =
        mockMvc
            .perform(
                multipart("/api/v1/documents")
                    .file(file)
                    .param("documentType", "ID_CARD")
                    .with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.documentType", equalTo("ID_CARD")))
            .andExpect(jsonPath("$.data.scanStatus", equalTo("CLEAN")))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String documentId = com.jayway.jsonpath.JsonPath.read(uploadResponse, "$.data.id");

    assertThat(documentRepository.findAllByOwnerId(userId)).hasSize(1);

    mockMvc
        .perform(get("/api/v1/documents/" + documentId).with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.originalFilename", equalTo("id-card.png")));

    mockMvc
        .perform(
            get("/api/v1/documents/" + documentId + "/download-url")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.url").exists());
  }

  @Test
  void upload_rejectsUnsupportedFileType() throws Exception {
    UUID userId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "script.exe", "application/x-msdownload", new byte[] {1});

    mockMvc
        .perform(
            multipart("/api/v1/documents")
                .file(file)
                .param("documentType", "OTHER")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode", equalTo("UNSUPPORTED_FILE_TYPE")));
  }

  @Test
  void getDocument_returns404_forAnotherUsersDocument() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID intruder = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", "f.pdf", "application/pdf", new byte[] {1});

    String uploadResponse =
        mockMvc
            .perform(
                multipart("/api/v1/documents")
                    .file(file)
                    .param("documentType", "OTHER")
                    .with(jwt().jwt(j -> j.subject(owner.toString()))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String documentId = com.jayway.jsonpath.JsonPath.read(uploadResponse, "$.data.id");

    mockMvc
        .perform(
            get("/api/v1/documents/" + documentId).with(jwt().jwt(j -> j.subject(intruder.toString()))))
        .andExpect(status().isNotFound());
  }
}
