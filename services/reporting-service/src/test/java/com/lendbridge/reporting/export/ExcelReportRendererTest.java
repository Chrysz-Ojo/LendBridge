package com.lendbridge.reporting.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.reporting.dto.ReportTable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelReportRendererTest {

  @Test
  void simpleTable_rendersReadableWorkbookWithHeaderAndRows() throws IOException {
    ReportTable table =
        ReportTable.builder()
            .headers(List.of("status", "count"))
            .rows(List.of(List.of("LISTED", "3"), List.of("SUBMITTED", "5")))
            .build();

    byte[] xlsx = ExcelReportRenderer.render(table, "loan-pipeline");

    assertThat(xlsx).isNotEmpty();
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      XSSFSheet sheet = workbook.getSheetAt(0);
      assertThat(workbook.getSheetName(0)).isEqualTo("loan-pipeline");

      Row header = sheet.getRow(0);
      assertThat(header.getCell(0).getStringCellValue()).isEqualTo("status");
      assertThat(header.getCell(1).getStringCellValue()).isEqualTo("count");
      int headerFontIndex = header.getCell(0).getCellStyle().getFontIndexAsInt();
      assertThat(workbook.getFontAt(headerFontIndex).getBold()).isTrue();

      Row firstDataRow = sheet.getRow(1);
      assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("LISTED");
      assertThat(firstDataRow.getCell(1).getStringCellValue()).isEqualTo("3");
      assertThat(sheet.getLastRowNum()).isEqualTo(2);
    }
  }

  @Test
  void sheetNameWithInvalidCharacters_isSanitized() throws IOException {
    ReportTable table = ReportTable.builder().headers(List.of("a")).rows(List.of()).build();

    byte[] xlsx = ExcelReportRenderer.render(table, "a/b:c*d");

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      assertThat(workbook.getSheetName(0)).doesNotContain("/", ":", "*");
    }
  }

  @Test
  void emptyRows_rendersHeaderOnlyWorkbook() throws IOException {
    ReportTable table = ReportTable.builder().headers(List.of("a", "b")).rows(List.of()).build();

    byte[] xlsx = ExcelReportRenderer.render(table, "empty-report");

    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
      XSSFSheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getLastRowNum()).isZero();
    }
  }
}
