package com.lendbridge.wallet.model;

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
 * {@code accountNumber} is encrypted at rest (AES-GCM via {@link EncryptedStringConverter}), so
 * duplicate-account detection cannot be a database unique constraint on the ciphertext column —
 * {@code FieldCipher} uses a fresh random IV per encryption, so the same plaintext number never
 * produces the same ciphertext twice. Uniqueness per wallet is instead enforced in
 * {@code BankAccountServiceImpl} by decrypting and comparing against the wallet's existing
 * accounts before insert.
 */
@Entity
@Table(name = "bank_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAccount {

  @Id private UUID id;

  @Column(name = "wallet_id", nullable = false)
  private UUID walletId;

  @Column(name = "bank_code", nullable = false)
  private String bankCode;

  @Column(name = "bank_name")
  private String bankName;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "account_number_ciphertext", nullable = false)
  private String accountNumber;

  @Column(name = "account_holder_name", nullable = false)
  private String accountHolderName;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_status", nullable = false)
  private BankVerificationStatus verificationStatus;

  @Column(name = "verified_at")
  private Instant verifiedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
