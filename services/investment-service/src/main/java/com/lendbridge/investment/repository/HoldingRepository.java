package com.lendbridge.investment.repository;

import com.lendbridge.investment.model.Holding;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {}
