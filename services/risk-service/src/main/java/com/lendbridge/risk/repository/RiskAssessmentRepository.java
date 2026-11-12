package com.lendbridge.risk.repository;

import com.lendbridge.risk.model.RiskAssessment;
import com.lendbridge.risk.model.RiskAssessmentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, UUID> {

  /** Backs the retroactive-grading query a {@code kyc.verified}/{@code kyc.rejected} consumer
   * runs once a borrower's trust profile resolves — a borrower can have multiple loans submitted
   * before their KYC clears (see the approved plan's processing logic). Index on
   * {@code (borrower_user_id, status)} in {@code 002-risk-assessment.yaml} backs this. */
  List<RiskAssessment> findAllByBorrowerUserIdAndStatus(UUID borrowerUserId, RiskAssessmentStatus status);
}
