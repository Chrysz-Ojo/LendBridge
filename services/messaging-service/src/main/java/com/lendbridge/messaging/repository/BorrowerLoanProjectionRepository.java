package com.lendbridge.messaging.repository;

import com.lendbridge.messaging.model.BorrowerLoanProjection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerLoanProjectionRepository extends JpaRepository<BorrowerLoanProjection, UUID> {}
