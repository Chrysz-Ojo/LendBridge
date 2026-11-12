package com.lendbridge.risk.repository;

import com.lendbridge.risk.model.BorrowerTrustProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerTrustProfileRepository extends JpaRepository<BorrowerTrustProfile, UUID> {}
