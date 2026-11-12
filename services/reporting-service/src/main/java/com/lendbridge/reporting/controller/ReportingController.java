package com.lendbridge.reporting.controller;

import com.lendbridge.reporting.dto.ReportQueryRequest;
import com.lendbridge.reporting.dto.ReportTable;
import com.lendbridge.reporting.export.CsvRenderer;
import com.lendbridge.reporting.export.ExcelReportRenderer;
import com.lendbridge.reporting.export.PdfReportRenderer;
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
 * or as a download — CSV, PDF, or Excel ({@code format=csv|pdf|excel}) — the one deliberate
 * exception to "every successful response wrapped in ApiResponse", since a file download can't
 * also be a JSON envelope. Gated to {@code SUPER_ADMIN} alone, same "most sensitive cross-domain
 * read surface" reasoning as audit-service's query API. */
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
    return respond("user-growth", from, to, format, reportingQueryService::userGrowth);
  }

  @GetMapping("/loan-pipeline")
  public ResponseEntity<?> loanPipeline(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond("loan-pipeline", from, to, format, reportingQueryService::loanPipeline);
  }

  @GetMapping("/risk-grades")
  public ResponseEntity<?> riskGrades(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond("risk-grades", from, to, format, reportingQueryService::riskGrades);
  }

  @GetMapping("/investment-activity")
  public ResponseEntity<?> investmentActivity(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond("investment-activity", from, to, format, reportingQueryService::investmentActivity);
  }

  @GetMapping("/kyc-approval-rate")
  public ResponseEntity<?> kycApprovalRate(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
      @RequestParam(required = false, defaultValue = "json") String format) {
    return respond("kyc-approval-rate", from, to, format, reportingQueryService::kycApprovalRate);
  }

  private static ResponseEntity<?> respond(
      String reportName, Instant from, Instant to, String format, Function<ReportQueryRequest, ReportTable> query) {
    ReportTable table = query.apply(ReportQueryRequest.builder().from(from).to(to).build());
    return switch (format == null ? "json" : format.toLowerCase()) {
      case "csv" ->
          download(reportName, "csv", "text/csv", CsvRenderer.render(table).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      case "pdf" -> download(reportName, "pdf", "application/pdf", PdfReportRenderer.render(table, reportName));
      case "excel", "xlsx" ->
          download(
              reportName,
              "xlsx",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
              ExcelReportRenderer.render(table, reportName));
      default -> ResponseEntity.ok(ApiResponse.success(table));
    };
  }

  private static ResponseEntity<byte[]> download(
      String reportName, String extension, String contentType, byte[] body) {
    return ResponseEntity.ok()
        .contentType(MediaType.valueOf(contentType))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + reportName + "." + extension + "\"")
        .body(body);
  }
}
