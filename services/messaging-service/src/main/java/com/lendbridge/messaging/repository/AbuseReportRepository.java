package com.lendbridge.messaging.repository;

import com.lendbridge.messaging.model.AbuseReport;
import com.lendbridge.messaging.model.AbuseReportStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbuseReportRepository extends JpaRepository<AbuseReport, UUID> {

  List<AbuseReport> findByStatusOrderByCreatedAtAsc(AbuseReportStatus status);
}
