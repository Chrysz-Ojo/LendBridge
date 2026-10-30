package com.lendbridge.loan.repository;

import com.lendbridge.loan.model.LoanApplication;
import com.lendbridge.loan.model.LoanStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

  List<LoanApplication> findAllByBorrowerUserIdOrderByCreatedAtDesc(UUID borrowerUserId);

  List<LoanApplication> findAllByStatusOrderByCreatedAtAsc(LoanStatus status);
}
