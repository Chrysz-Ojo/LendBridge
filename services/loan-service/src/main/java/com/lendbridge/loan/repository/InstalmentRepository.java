package com.lendbridge.loan.repository;

import com.lendbridge.loan.model.Instalment;
import com.lendbridge.loan.model.InstalmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstalmentRepository extends JpaRepository<Instalment, UUID> {

  List<Instalment> findAllByLoanIdOrderByInstalmentNumberAsc(UUID loanId);

  Optional<Instalment> findFirstByLoanIdAndStatusOrderByInstalmentNumberAsc(UUID loanId, InstalmentStatus status);

  /** Used to detect "every instalment is now Paid" without loading the whole schedule. */
  boolean existsByLoanIdAndStatusNot(UUID loanId, InstalmentStatus status);
}
