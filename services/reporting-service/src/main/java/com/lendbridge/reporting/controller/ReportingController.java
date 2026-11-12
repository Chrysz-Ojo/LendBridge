package com.lendbridge.reporting.controller;

import com.lendbridge.reporting.csv.CsvRenderer;
import com.lendbridge.reporting.dto.ReportQueryRequest;
import com.lendbridge.reporting.dto.ReportTable;
import com.lendbridge.reporting.service.ReportingQueryService;
import com.lendbridge.starter.dto.ApiResponse;
import java.time.Instant;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Five management reports (PRD E11-S5), each available as JSON ({@code ApiResponse<ReportTable>})
 * or CSV (raw {@code text/csv} body — the one deliberate exception to "every successful response
 * wrapped in ApiResponse", since a CSV download can't also be a JSON envelope). Gated to
 * {@code SUPER_ADMIN} alone, same "most sensitive cross-domain read surface" reasoning as
 * audit-service's query API. */
@RestController
@RequestMapping("/api/v1/reporting")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ReportingController {

  private final ReportingQueryService reportingQueryService;

  @GetMapping("/user-growth")
  public ResponseEntity<?> userGrowth(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond(from, to, format, reportingQueryService::userGrowth);
  }

  @GetMapping("/loan-pipeline")
  public ResponseEntity<?> loanPipeline(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond(from, to, format, reportingQueryService::loanPipeline);
  }

  @GetMapping("/risk-grades")
  public ResponseEntity<?> riskGrades(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond(from, to, format, reportingQueryService::riskGrades);
  }

  @GetMapping("/investment-activity")
  public ResponseEntity<?> investmentActivity(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond(from, to, format, reportingQueryService::investmentActivity);
  }

  @GetMapping("/kyc-approval-rate")
  public ResponseEntity<?> kycApprovalRate(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond(from, to, format, reportingQueryService::kycApprovalRate);
  }

  private static ResponseEntity<?> respond(
      Instant from, Instant to, String format, Function<ReportQueryRequest, ReportTable> query) {
    ReportTable table = query.apply(ReportQueryRequest.builder().from(from).to(to).build());
    if ("csv".equalsIgnoreCase(format)) {
      return ResponseEntity.ok()
          .contentType(MediaType.valueOf("text/csv"))
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.csv\"")
          .body(CsvRenderer.render(table));
    }
    return ResponseEntity.ok(ApiResponse.success(table));
  }
}
