package com.lendbridge.payment.service;

import com.lendbridge.payment.dto.ReconciliationRunRequest;
import com.lendbridge.payment.dto.ReconciliationRunResponse;
import java.util.UUID;

public interface ReconciliationService {

  ReconciliationRunResponse runReconciliation(ReconciliationRunRequest request);

  ReconciliationRunResponse getReconciliationRun(UUID id);
}
