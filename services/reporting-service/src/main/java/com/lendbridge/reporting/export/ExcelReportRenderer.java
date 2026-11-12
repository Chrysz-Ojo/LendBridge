package com.lendbridge.reporting.export;

import com.lendbridge.reporting.dto.ReportTable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Renders a {@link ReportTable} as a real {@code .xlsx} workbook with Apache POI — one sheet, a
 * bold header row, auto-sized columns. Shared by every report endpoint (generic on
 * {@code ReportTable}), same spirit as {@link CsvRenderer}/{@link PdfReportRenderer}.
 */
public final class ExcelReportRenderer {

  private ExcelReportRenderer() {}

  public static byte[] render(ReportTable table, String sheetName) {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      XSSFSheet sheet = workbook.createSheet(safeSheetName(sheetName));

      CellStyle headerStyle = workbook.createCellStyle();
      Font boldFont = workbook.createFont();
      boldFont.setBold(true);
      headerStyle.setFont(boldFont);

      List<String> headers = table.getHeaders();
      Row headerRow = sheet.createRow(0);
      for (int col = 0; col < headers.size(); col++) {
        Cell cell = headerRow.createCell(col);
        cell.setCellValue(headers.get(col));
        cell.setCellStyle(headerStyle);
      }

      List<List<String>> rows = table.getRows();
      for (int r = 0; r < rows.size(); r++) {
        Row dataRow = sheet.createRow(r + 1);
        List<String> rowValues = rows.get(r);
        for (int col = 0; col < rowValues.size(); col++) {
          dataRow.createCell(col).setCellValue(rowValues.get(col) == null ? "" : rowValues.get(col));
        }
      }

      for (int col = 0; col < headers.size(); col++) {
        sheet.autoSizeColumn(col);
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      workbook.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to render report Excel workbook", e);
    }
  }

  /** Excel sheet names can't exceed 31 chars or contain {@code \/?*[]:} — truncate and strip rather
   * than let an otherwise-valid report name blow up workbook creation. */
  private static String safeSheetName(String name) {
    String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", "-");
    return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
  }
}
