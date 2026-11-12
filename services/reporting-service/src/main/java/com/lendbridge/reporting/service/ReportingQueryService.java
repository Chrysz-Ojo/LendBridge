package com.lendbridge.reporting.service;

import com.lendbridge.reporting.dto.ReportQueryRequest;
import com.lendbridge.reporting.dto.ReportTable;

public interface ReportingQueryService {

  ReportTable userGrowth(ReportQueryRequest request);

  ReportTable loanPipeline(ReportQueryRequest request);

  ReportTable riskGrades(ReportQueryRequest request);

  ReportTable investmentActivity(ReportQueryRequest request);

  ReportTable kycApprovalRate(ReportQueryRequest request);
}
