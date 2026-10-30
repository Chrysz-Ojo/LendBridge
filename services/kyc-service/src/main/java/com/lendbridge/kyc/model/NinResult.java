package com.lendbridge.kyc.model;

import com.lendbridge.starter.jpa.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * Identical shape to {@link BvnResult} for NIN — see that class's javadoc. NIN verification is an
 * extension beyond the PRD's literal BVN-only text; see ADR 0014.
 */
@Entity
@Table(name = "nin_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NinResult {

  @Id private UUID id;

  @Column(name = "verification_case_id", nullable = false)
  private UUID verificationCaseId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "nin_ciphertext", nullable = false, columnDefinition = "text")
  private String nin;

  @Column(name = "nin_hash", nullable = false)
  private String ninHash;

  @Column(name = "name_match")
  private Boolean nameMatch;

  @Column(name = "dob_match")
  private Boolean dobMatch;

  @Enumerated(EnumType.STRING)
  @Column(name = "outcome", nullable = false)
  private CheckOutcome outcome;

  @Column(name = "raw_response", columnDefinition = "text")
  private String rawResponse;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;
}
