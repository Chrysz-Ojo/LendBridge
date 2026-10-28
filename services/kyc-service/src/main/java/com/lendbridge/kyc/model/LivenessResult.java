package com.lendbridge.kyc.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Covers both the "Liveness" and "ID" verification badges from PRD E5-S3 in one row: Smile
 * ID/Dojah/Prembly all bundle selfie liveness and selfie-vs-ID face-match into a single biometric
 * job, so a separate "id_result" table would just duplicate this one (see the approved plan's
 * "Data model" section).
 */
@Entity
@Table(name = "liveness_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LivenessResult {

  @Id private UUID id;

  @Column(name = "verification_case_id", nullable = false)
  private UUID verificationCaseId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Column(name = "selfie_document_id", nullable = false)
  private UUID selfieDocumentId;

  @Column(name = "id_card_document_id", nullable = false)
  private UUID idCardDocumentId;

  @Column(name = "liveness_score")
  private Double livenessScore;

  @Column(name = "liveness_passed")
  private Boolean livenessPassed;

  @Column(name = "face_match_score")
  private Double faceMatchScore;

  @Column(name = "face_match_passed")
  private Boolean faceMatchPassed;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false)
  private CheckOutcome outcome;

  @Column(name = "raw_response", columnDefinition = "text")
  private String rawResponse;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;
}
