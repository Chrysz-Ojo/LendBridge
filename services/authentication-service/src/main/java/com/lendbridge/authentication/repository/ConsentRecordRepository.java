package com.lendbridge.authentication.repository;

import com.lendbridge.authentication.model.ConsentRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {}
