package com.lendbridge.loan.document;

import com.lendbridge.starter.dto.ApiResponse;
import com.lendbridge.starter.exception.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
 *
 * <p>The multipart body is built by hand rather than with {@code MultipartBodyBuilder}: that
 * class's {@code part(...)} checks {@code instanceof org.springframework.http.codec.multipart.Part}
 * internally, and loading that (reactive-world) interface requires {@code reactive-streams} on the
 * classpath even for this purely synchronous use — a dependency this module deliberately doesn't
 * carry (same reasoning as skipping {@code feign-form} above).
 */
@Component
public class DocumentUploadClient {

  private static final Logger log = LoggerFactory.getLogger(DocumentUploadClient.class);
  private static final String BOUNDARY_PREFIX = "LendBridgeBoundary";

  private final RestClient restClient;

  public DocumentUploadClient(
      RestClient.Builder builder, @Value("${document-service.url}") String documentServiceUrl) {
    this.restClient = builder.baseUrl(documentServiceUrl).build();
  }

  @CircuitBreaker(name = "documentService", fallbackMethod = "uploadFallback")
  @Retry(name = "documentService")
  public DocumentUploadResponse uploadAgreement(byte[] pdfBytes, String filename) {
    String boundary = BOUNDARY_PREFIX + UUID.randomUUID();
    byte[] body = multipartBody(boundary, filename, pdfBytes);

    RestClient.RequestBodySpec request =
        restClient
            .post()
            .uri(
                uriBuilder ->
                    uriBuilder.path("/api/v1/documents").queryParam("documentType", "LOAN_AGREEMENT").build())
            .contentType(MediaType.valueOf("multipart/form-data; boundary=" + boundary));
    forwardAuthorization(request);

    ApiResponse<DocumentUploadResponse> response = request.body(body).retrieve().body(new ParameterizedTypeReference<>() {});
    return response.getData();
  }

  private byte[] multipartBody(String boundary, String filename, byte[] pdfBytes) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
      out.write(
          ("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
              .getBytes(StandardCharsets.UTF_8));
      out.write("Content-Type: application/pdf\r\n\r\n".getBytes(StandardCharsets.UTF_8));
      out.write(pdfBytes);
      out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
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
