package com.lendbridge.reporting.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The one shape every report query returns, whether rendered as JSON ({@code ApiResponse<ReportTable>})
 * or streamed as CSV via {@link com.lendbridge.reporting.csv.CsvRenderer}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTable {
  private List<String> headers;
  private List<List<String>> rows;
}
