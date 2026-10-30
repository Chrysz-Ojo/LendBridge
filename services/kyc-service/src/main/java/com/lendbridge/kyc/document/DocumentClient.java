package com.lendbridge.kyc.document;

import com.lendbridge.starter.dto.ApiResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "document-service",
    url = "${DOCUMENT_SERVICE_URL:http://localhost:8083}",
    configuration = DocumentFeignConfig.class)
public interface DocumentClient {

  @GetMapping("/api/v1/documents/{id}")
  ApiResponse<DocumentResponse> getDocument(@PathVariable("id") UUID id);
}
