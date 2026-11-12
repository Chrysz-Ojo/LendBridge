package com.lendbridge.reporting.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The one shape every report query returns, whether rendered as JSON ({@code ApiResponse<ReportTable>})
 * or streamed as CSV/PDF/Excel via {@link com.lendbridge.reporting.export.CsvRenderer},
 * {@link com.lendbridge.reporting.export.PdfReportRenderer}, or
 * {@link com.lendbridge.reporting.export.ExcelReportRenderer}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTable {
  private List<String> headers;
  private List<List<String>> rows;
}
