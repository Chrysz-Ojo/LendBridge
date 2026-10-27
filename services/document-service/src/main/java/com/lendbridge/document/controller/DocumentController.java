package com.lendbridge.document.controller;

import com.lendbridge.document.dto.DocumentResponse;
import com.lendbridge.document.dto.DownloadUrlResponse;
import com.lendbridge.document.dto.UploadDocumentRequest;
import com.lendbridge.document.model.DocumentType;
import com.lendbridge.document.security.CurrentUser;
import com.lendbridge.document.service.DocumentService;
import com.lendbridge.starter.dto.ApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

  private final DocumentService documentService;

  @PostMapping
  public ResponseEntity<ApiResponse<DocumentResponse>> upload(
      Authentication authentication,
      @RequestParam("documentType") DocumentType documentType,
      @RequestPart("file") MultipartFile file) {
    DocumentResponse response =
        documentService.uploadDocument(
            CurrentUser.idFrom(authentication), new UploadDocumentRequest(documentType), file);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<List<DocumentResponse>>> listMine(Authentication authentication) {
    List<DocumentResponse> response = documentService.listDocuments(CurrentUser.idFrom(authentication));
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
      Authentication authentication, @PathVariable UUID id) {
    DocumentResponse response = documentService.getDocument(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{id}/download-url")
  public ResponseEntity<ApiResponse<DownloadUrlResponse>> getDownloadUrl(
      Authentication authentication, @PathVariable UUID id) {
    DownloadUrlResponse response = documentService.getDownloadUrl(CurrentUser.idFrom(authentication), id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }
}
