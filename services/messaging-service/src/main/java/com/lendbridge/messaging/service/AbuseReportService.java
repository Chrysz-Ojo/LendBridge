package com.lendbridge.messaging.service;

import com.lendbridge.messaging.dto.AbuseReportRequest;
import com.lendbridge.messaging.dto.AbuseReportResponse;
import java.util.List;
import java.util.UUID;

public interface AbuseReportService {

  AbuseReportResponse file(UUID conversationId, AbuseReportRequest request, UUID reporterUserId);

  List<AbuseReportResponse> findOpen();

  AbuseReportResponse resolve(UUID reportId);
}
