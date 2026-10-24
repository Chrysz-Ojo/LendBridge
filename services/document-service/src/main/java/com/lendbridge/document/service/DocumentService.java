package com.lendbridge.document.service;

import com.lendbridge.document.dto.DocumentResponse;
import com.lendbridge.document.dto.DownloadUrlResponse;
import com.lendbridge.document.dto.UploadDocumentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

  DocumentResponse uploadDocument(UUID ownerId, UploadDocumentRequest request, MultipartFile file);

  DocumentResponse getDocument(UUID ownerId, UUID documentId);

  DownloadUrlResponse getDownloadUrl(UUID ownerId, UUID documentId);

  List<DocumentResponse> listDocuments(UUID ownerId);
}
