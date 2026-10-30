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
 * {@code bvnCiphertext} is AES-GCM encrypted at rest via {@link EncryptedStringConverter} (per
 * CLAUDE.md's DTO style and user-profile-service's {@code UserProfile.phone} precedent).
 * {@code bvnHash} is a deterministic HMAC-SHA256(bvn, pepper) computed by {@code IdentityHasher},
 * stored alongside the non-deterministic GCM ciphertext so a BVN can be looked up/compared for
 * duplicate-fraud detection without ever decrypting it (PRD: "BVN is also stored as a salted hash
 * for lookups").
 */
@Entity
@Table(name = "bvn_result")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BvnResult {

  @Id private UUID id;

  @Column(name = "verification_case_id", nullable = false)
  private UUID verificationCaseId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false)
  private ProviderName provider;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "bvn_ciphertext", nullable = false, columnDefinition = "text")
  private String bvn;

  @Column(name = "bvn_hash", nullable = false)
  private String bvnHash;

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
