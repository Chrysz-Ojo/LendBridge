package com.lendbridge.document.service.impl;

import com.lendbridge.document.dto.DocumentResponse;
import com.lendbridge.document.dto.DownloadUrlResponse;
import com.lendbridge.document.dto.UploadDocumentRequest;
import com.lendbridge.document.mapper.DocumentMapper;
import com.lendbridge.document.model.Document;
import com.lendbridge.document.model.ScanStatus;
import com.lendbridge.document.outbox.DocumentUploadedPayload;
import com.lendbridge.document.outbox.KafkaTopics;
import com.lendbridge.document.repository.DocumentRepository;
import com.lendbridge.document.scan.ScanResult;
import com.lendbridge.document.scan.VirusScanner;
import com.lendbridge.document.service.DocumentService;
import com.lendbridge.document.storage.ObjectStore;
import com.lendbridge.starter.crypto.UuidV7;
import com.lendbridge.starter.exception.BadRequestException;
import com.lendbridge.starter.exception.NotFoundException;
import com.lendbridge.starter.outbox.OutboxEventWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "application/pdf");
  private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
  private static final Duration DOWNLOAD_URL_EXPIRY = Duration.ofMinutes(5);

  private final DocumentRepository documentRepository;
  private final ObjectStore objectStore;
  private final VirusScanner virusScanner;
  private final OutboxEventWriter outboxEventWriter;
  private final DocumentMapper documentMapper;

  @Override
  @Transactional
  public DocumentResponse uploadDocument(UUID ownerId, UploadDocumentRequest request, MultipartFile file) {
    validate(file);

    byte[] content = readBytes(file);
    if (virusScanner.scan(content) != ScanResult.CLEAN) {
      throw new BadRequestException("FILE_INFECTED", "File failed virus scan and was rejected");
    }

    UUID id = UuidV7.generate();
    String objectKey = buildObjectKey(ownerId, id, file.getOriginalFilename());
    objectStore.put(objectKey, content, file.getContentType());

    Instant now = Instant.now();
    Document document =
        Document.builder()
            .id(id)
            .ownerId(ownerId)
            .documentType(request.getDocumentType())
            .originalFilename(file.getOriginalFilename())
            .contentType(file.getContentType())
            .sizeBytes(file.getSize())
            .checksumSha256(sha256Hex(content))
            .objectKey(objectKey)
            .scanStatus(ScanStatus.CLEAN)
            .createdAt(now)
            .build();
    documentRepository.save(document);

    outboxEventWriter.write(
        "Document",
        id.toString(),
        KafkaTopics.DOCUMENT_UPLOADED,
        new DocumentUploadedPayload(
            id.toString(), ownerId.toString(), request.getDocumentType().name(), now.toEpochMilli()));

    return documentMapper.toResponse(document);
  }

  @Override
  @Transactional(readOnly = true)
  public DocumentResponse getDocument(UUID ownerId, UUID documentId) {
    return documentMapper.toResponse(findOrThrow(ownerId, documentId));
  }

  @Override
  @Transactional(readOnly = true)
  public DownloadUrlResponse getDownloadUrl(UUID ownerId, UUID documentId) {
    Document document = findOrThrow(ownerId, documentId);
    var url = objectStore.presignedGetUrl(document.getObjectKey(), DOWNLOAD_URL_EXPIRY);
    return DownloadUrlResponse.builder()
        .url(url.toString())
        .expiresAtEpochMillis(Instant.now().plus(DOWNLOAD_URL_EXPIRY).toEpochMilli())
        .build();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DocumentResponse> listDocuments(UUID ownerId) {
    return documentRepository.findAllByOwnerId(ownerId).stream().map(documentMapper::toResponse).toList();
  }

  private Document findOrThrow(UUID ownerId, UUID documentId) {
    return documentRepository
        .findByIdAndOwnerId(documentId, ownerId)
        .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
  }

  private void validate(MultipartFile file) {
    if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
      throw new BadRequestException(
          "UNSUPPORTED_FILE_TYPE", "Only JPG, PNG and PDF files are accepted");
    }
    if (file.getSize() > MAX_SIZE_BYTES) {
      throw new BadRequestException("FILE_TOO_LARGE", "File must be 10 MB or smaller");
    }
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Unable to read uploaded file content", e);
    }
  }

  private String buildObjectKey(UUID ownerId, UUID documentId, String originalFilename) {
    String safeFilename =
        originalFilename == null ? "file" : originalFilename.replaceAll("[\\\\/]", "_");
    return "documents/" + ownerId + "/" + documentId + "/" + safeFilename;
  }

  private String sha256Hex(byte[] content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
