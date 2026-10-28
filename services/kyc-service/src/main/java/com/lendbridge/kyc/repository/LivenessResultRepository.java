package com.lendbridge.kyc.repository;

import com.lendbridge.kyc.model.LivenessResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivenessResultRepository extends JpaRepository<LivenessResult, UUID> {

  Optional<LivenessResult> findByVerificationCaseId(UUID verificationCaseId);
}
