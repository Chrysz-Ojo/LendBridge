package com.lendbridge.kyc.repository;

import com.lendbridge.kyc.model.BvnResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BvnResultRepository extends JpaRepository<BvnResult, UUID> {

  Optional<BvnResult> findByVerificationCaseId(UUID verificationCaseId);

  /** Duplicate-BVN fraud lookup without decrypting any ciphertext — see {@link BvnResult}'s
   * javadoc. Not yet wired to an endpoint; a capability the stored hash enables for later use. */
  List<BvnResult> findAllByBvnHash(String bvnHash);
}
