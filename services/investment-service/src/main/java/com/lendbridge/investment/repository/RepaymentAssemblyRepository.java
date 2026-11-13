package com.lendbridge.investment.repository;

import com.lendbridge.investment.model.RepaymentAssembly;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepaymentAssemblyRepository extends JpaRepository<RepaymentAssembly, UUID> {}
