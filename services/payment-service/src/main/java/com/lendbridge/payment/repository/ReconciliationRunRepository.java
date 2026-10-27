package com.lendbridge.payment.repository;

import com.lendbridge.payment.model.ReconciliationRun;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {}
