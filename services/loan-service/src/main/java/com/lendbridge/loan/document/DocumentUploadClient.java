package com.lendbridge.loan.document;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Uploads a generated agreement PDF to document-service, forwarding the current end-user's own
 * JWT so the document lands under the borrower's own id (document-service scopes ownership to the
 * calling user — see {@code DocumentController}). Plain {@link RestClient}, not Feign: Feign needs
 * an extra multipart encoder library ({@code feign-form}) not in the approved stack, and no service
 * in this repo uploads a file via Feign today — {@code RestClient} ships with
 * {@code spring-boot-starter-web} already on the classpath. Still wrapped in
 * {@code @CircuitBreaker}/{@code @Retry} exactly like the Feign-based gateways, since those
 * annotations are AOP on the Spring bean method, not Feign-specific.
 */
@Component
public class DocumentUploadClient {

  private static final Logger log = LoggerFactory.getLogger(DocumentUploadClient.class);

  private final RestClient restClient;

  public DocumentUploadClient(
      RestClient.Builder builder, @Value("${document-service.url}") String documentServiceUrl) {
    this.restClient = builder.baseUrl(documentServiceUrl).build();
  }

  @CircuitBreaker(name = "documentService", fallbackMethod = "uploadFallback")
  @Retry(name = "documentService")
  public DocumentUploadResponse uploadAgreement(byte[] pdfBytes, String filename) {
    MultipartBodyBuilder multipart = new MultipartBodyBuilder();
    multipart
        .part(
            "file",
            new ByteArrayResource(pdfBytes) {
              @Override
              public String getFilename() {
                return filename;
              }
            })
        .contentType(MediaType.APPLICATION_PDF);

    RestClient.RequestBodySpec request =
        restClient
            .post()
            .uri(
                uriBuilder ->
                    uriBuilder.path("/api/v1/documents").queryParam("documentType", "LOAN_AGREEMENT").build())
            .contentType(MediaType.MULTIPART_FORM_DATA);
    forwardAuthorization(request);

    ApiResponse<DocumentUploadResponse> response =
        request.body(multipart.build()).retrieve().body(new ParameterizedTypeReference<>() {});
    return response.getData();
  }

  private void forwardAuthorization(RestClient.RequestBodySpec request) {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return;
    }
    String authorization = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    if (authorization != null) {
      request.header(HttpHeaders.AUTHORIZATION, authorization);
    }
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as uploadAgreement's fallback
  private DocumentUploadResponse uploadFallback(byte[] pdfBytes, String filename, Throwable cause) {
    log.warn("document-service unreachable while uploading agreement PDF", cause);
    throw new DependencyUnavailableException(
        "DOCUMENT_SERVICE_UNAVAILABLE", "Unable to upload agreement document: " + cause.getMessage());
  }
}
