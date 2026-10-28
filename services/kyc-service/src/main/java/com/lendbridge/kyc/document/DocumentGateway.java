package com.lendbridge.kyc.document;

import com.lendbridge.starter.exception.DependencyUnavailableException;
import com.lendbridge.starter.exception.UnprocessableEntityException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only thing the rest of kyc-service calls to reach document-service. Resilience4j annotations
 * live here, not on {@link DocumentClient} directly — Feign clients are Spring-generated proxies,
 * and wrapping the call in an explicit service method avoids proxy-on-proxy ambiguity (same
 * pattern as wallet-service's {@code LedgerGateway}).
 */
@Service
public class DocumentGateway {

  private static final Logger log = LoggerFactory.getLogger(DocumentGateway.class);

  private final DocumentClient documentClient;

  public DocumentGateway(DocumentClient documentClient) {
    this.documentClient = documentClient;
  }

  @CircuitBreaker(name = "documentService", fallbackMethod = "fetchFallback")
  @Retry(name = "documentService")
  public DocumentResponse fetch(UUID documentId) {
    return documentClient.getDocument(documentId).getData();
  }

  @SuppressWarnings("unused") // invoked reflectively by Resilience4j as fetch's fallback
  private DocumentResponse fetchFallback(UUID documentId, Throwable cause) {
    log.warn("document-service unreachable for document {}", documentId, cause);
    throw new DependencyUnavailableException(
        "DOCUMENT_SERVICE_UNAVAILABLE", "Unable to fetch document " + documentId + ": " + cause.getMessage());
  }

  /**
   * Fetches a document and enforces the two preconditions every KYC check needs before it can use
   * one (per the approved plan's infra-wiring section): it must have passed virus scanning and its
   * declared type must match what the check expects. Neither failure is a provider verdict — both
   * surface as {@link UnprocessableEntityException}, leaving any in-progress verification case
   * untouched (outcome routing rule 4).
   */
  public DocumentResponse fetchAndValidate(UUID documentId, DocumentType expectedType) {
    DocumentResponse document = fetch(documentId);
    if (document.getDocumentType() != expectedType) {
      throw new UnprocessableEntityException(
          "DOCUMENT_TYPE_MISMATCH",
          "Expected document type " + expectedType + " but document " + documentId + " is " + document.getDocumentType());
    }
    if (document.getScanStatus() != ScanStatus.CLEAN) {
      throw new UnprocessableEntityException(
          "DOCUMENT_NOT_CLEAN", "Document " + documentId + " has not passed virus scanning");
    }
    return document;
  }
}
