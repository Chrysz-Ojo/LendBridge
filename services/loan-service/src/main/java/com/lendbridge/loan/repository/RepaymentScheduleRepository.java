package com.lendbridge.loan.repository;

import com.lendbridge.loan.model.RepaymentSchedule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, UUID> {}
