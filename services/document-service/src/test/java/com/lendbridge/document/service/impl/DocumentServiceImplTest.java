package com.lendbridge.document.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lendbridge.document.dto.UploadDocumentRequest;
import com.lendbridge.document.mapper.DocumentMapper;
import com.lendbridge.document.model.Document;
import com.lendbridge.document.model.DocumentType;
import com.lendbridge.document.repository.DocumentRepository;
import com.lendbridge.document.scan.ScanResult;
import com.lendbridge.document.scan.VirusScanner;
import com.lendbridge.document.storage.ObjectStore;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

  @Mock private DocumentRepository documentRepository;
  @Mock private ObjectStore objectStore;
  @Mock private VirusScanner virusScanner;
  @Mock private OutboxEventWriter outboxEventWriter;
  private DocumentMapper documentMapper;

  private DocumentServiceImpl service;
  private UUID ownerId;

  @BeforeEach
  void setUp() {
    documentMapper = org.mapstruct.factory.Mappers.getMapper(DocumentMapper.class);
    service =
        new DocumentServiceImpl(documentRepository, objectStore, virusScanner, outboxEventWriter, documentMapper);
    ownerId = UUID.randomUUID();
  }

  @Test
  void uploadDocument_rejectsUnsupportedContentType() {
    MockMultipartFile file = new MockMultipartFile("file", "virus.exe", "application/x-msdownload", new byte[] {1});

    assertThatThrownBy(() -> service.uploadDocument(ownerId, new UploadDocumentRequest(DocumentType.OTHER), file))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void uploadDocument_rejectsOversizedFile() {
    byte[] tooLarge = new byte[11 * 1024 * 1024];
    MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", tooLarge);

    assertThatThrownBy(() -> service.uploadDocument(ownerId, new UploadDocumentRequest(DocumentType.OTHER), file))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void uploadDocument_rejectsInfectedFile() {
    when(virusScanner.scan(any())).thenReturn(ScanResult.INFECTED);
    MockMultipartFile file = new MockMultipartFile("file", "id.png", "image/png", new byte[] {1, 2, 3});

    assertThatThrownBy(() -> service.uploadDocument(ownerId, new UploadDocumentRequest(DocumentType.OTHER), file))
        .isInstanceOf(BadRequestException.class);

    verify(objectStore, never()).put(anyString(), any(), anyString());
    verify(documentRepository, never()).save(any());
    verify(outboxEventWriter, never()).write(any(), any(), any(), any());
  }

  @Test
  void uploadDocument_happyPath_storesScansAndPublishes() {
    when(virusScanner.scan(any())).thenReturn(ScanResult.CLEAN);
    MockMultipartFile file = new MockMultipartFile("file", "id.png", "image/png", new byte[] {1, 2, 3});

    var response = service.uploadDocument(ownerId, new UploadDocumentRequest(DocumentType.ID_CARD), file);

    assertThat(response.getDocumentType()).isEqualTo(DocumentType.ID_CARD);
    assertThat(response.getOriginalFilename()).isEqualTo("id.png");
    verify(objectStore).put(anyString(), any(), anyString());
    verify(documentRepository).save(any(Document.class));
    verify(outboxEventWriter).write(any(), any(), any(), any());
  }

  @Test
  void getDocument_throwsNotFound_whenNotOwnedByCaller() {
    when(documentRepository.findByIdAndOwnerId(any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getDocument(ownerId, UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void getDownloadUrl_delegatesToObjectStore() throws Exception {
    Document document =
        Document.builder()
            .id(UUID.randomUUID())
            .ownerId(ownerId)
            .documentType(DocumentType.OTHER)
            .originalFilename("f.pdf")
            .contentType("application/pdf")
            .sizeBytes(10)
            .checksumSha256("abc")
            .objectKey("documents/x/y/f.pdf")
            .scanStatus(com.lendbridge.document.model.ScanStatus.CLEAN)
            .createdAt(Instant.now())
            .build();
    when(documentRepository.findByIdAndOwnerId(document.getId(), ownerId)).thenReturn(Optional.of(document));
    URL presigned = URI.create("http://localhost:9000/bucket/key?signed=1").toURL();
    when(objectStore.presignedGetUrl("documents/x/y/f.pdf", Duration.ofMinutes(5))).thenReturn(presigned);

    var response = service.getDownloadUrl(ownerId, document.getId());

    assertThat(response.getUrl()).isEqualTo(presigned.toString());
  }
}
