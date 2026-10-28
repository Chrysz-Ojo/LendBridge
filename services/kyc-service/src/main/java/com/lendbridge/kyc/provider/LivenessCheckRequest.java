package com.lendbridge.kyc.provider;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The approved plan scopes {@code DocumentClient} to document-service's {@code GET
 * /api/v1/documents/{id}} metadata endpoint only (no download-url/content fetch), so a real
 * adapter would need its own out-of-band way to reach the actual image bytes — out of scope here
 * since the real adapters are never invoked by anything automated (CLAUDE.md's external-provider
 * rule). {@code selfieFilenameHint}/{@code idCardFilenameHint} (each document's
 * {@code originalFilename} at check time) exist purely so {@code FakeVerificationProvider} can
 * deterministically route a test to PASSED/MISMATCH/FAILED, the same sentinel-in-a-free-text-field
 * technique {@code FakePaymentProvider} uses with its charge/payout reference.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LivenessCheckRequest {
  private UUID selfieDocumentId;
  private String selfieFilenameHint;
  private UUID idCardDocumentId;
  private String idCardFilenameHint;
}
