package com.lendbridge.kyc.repository;

import com.lendbridge.kyc.model.NinResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NinResultRepository extends JpaRepository<NinResult, UUID> {

  Optional<NinResult> findByVerificationCaseId(UUID verificationCaseId);

  /** Duplicate-NIN fraud lookup without decrypting any ciphertext — see {@link NinResult}'s
   * javadoc. Not yet wired to an endpoint; a capability the stored hash enables for later use. */
  List<NinResult> findAllByNinHash(String ninHash);
}
