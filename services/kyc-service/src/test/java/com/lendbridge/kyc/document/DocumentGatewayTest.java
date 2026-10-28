package com.lendbridge.kyc.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentGatewayTest {

  private final DocumentClient client = mock(DocumentClient.class);
  private final DocumentGateway gateway = new DocumentGateway(client);

  @Test
  void fetchAndValidate_returnsTheDocument_whenTypeMatchesAndScanIsClean() {
    UUID id = UUID.randomUUID();
    when(client.getDocument(id)).thenReturn(ApiResponse.success(document(id, DocumentType.ID_CARD, ScanStatus.CLEAN)));

    DocumentResponse response = gateway.fetchAndValidate(id, DocumentType.ID_CARD);

    assertThat(response.getId()).isEqualTo(id);
  }

  @Test
  void fetchAndValidate_rejectsAMismatchedDocumentType() {
    UUID id = UUID.randomUUID();
    when(client.getDocument(id)).thenReturn(ApiResponse.success(document(id, DocumentType.SELFIE, ScanStatus.CLEAN)));

    assertThatThrownBy(() -> gateway.fetchAndValidate(id, DocumentType.ID_CARD))
        .isInstanceOf(UnprocessableEntityException.class);
  }

  @Test
  void fetchAndValidate_rejectsADocumentThatHasNotPassedVirusScanning() {
    UUID id = UUID.randomUUID();
    when(client.getDocument(id)).thenReturn(ApiResponse.success(document(id, DocumentType.ID_CARD, ScanStatus.PENDING)));

    assertThatThrownBy(() -> gateway.fetchAndValidate(id, DocumentType.ID_CARD))
        .isInstanceOf(UnprocessableEntityException.class);
  }

  private DocumentResponse document(UUID id, DocumentType type, ScanStatus scanStatus) {
    return DocumentResponse.builder()
        .id(id)
        .documentType(type)
        .originalFilename("file.jpg")
        .contentType("image/jpeg")
        .sizeBytes(1024)
        .checksumSha256("deadbeef")
        .scanStatus(scanStatus)
        .createdAt(Instant.now())
        .build();
  }
}
