package com.lendbridge.audit.service;

import com.lendbridge.audit.dto.AuditRecordQueryRequest;
import com.lendbridge.audit.dto.AuditRecordResponse;
import com.lendbridge.audit.dto.RecordAuditEventRequest;
import java.util.List;

public interface AuditRecordService {

  void record(RecordAuditEventRequest request);

  List<AuditRecordResponse> query(AuditRecordQueryRequest request);
}
