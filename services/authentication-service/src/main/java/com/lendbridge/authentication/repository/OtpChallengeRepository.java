package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.model.OtpChallenge;
import com.lendbridge.authentication.model.OtpPurpose;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {

  List<OtpChallenge> findByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);
}
