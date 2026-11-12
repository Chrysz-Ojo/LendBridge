package com.lendbridge.reporting.csv;

import com.lendbridge.reporting.dto.ReportTable;

/** Hand-rolled CSV rendering (RFC 4180 quoting) shared by every report endpoint, so CSV-writing
 * logic exists exactly once instead of once per report. No CSV library added — the format is
 * simple enough (quote a field only if it contains a comma, quote, or newline; double up any
 * embedded quote) that a dependency isn't justified. */
public final class CsvRenderer {

  private static final String LINE_SEPARATOR = "\r\n";

  private CsvRenderer() {}

  public static String render(ReportTable table) {
    StringBuilder out = new StringBuilder();
    appendRow(out, table.getHeaders());
    for (java.util.List<String> row : table.getRows()) {
      appendRow(out, row);
    }
    return out.toString();
  }

  private static void appendRow(StringBuilder out, java.util.List<String> cells) {
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(escape(cells.get(i)));
    }
    out.append(LINE_SEPARATOR);
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    boolean needsQuoting = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
    if (!needsQuoting) {
      return value;
    }
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
