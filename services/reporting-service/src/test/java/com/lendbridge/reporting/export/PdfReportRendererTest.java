package com.lendbridge.reporting.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.reporting.dto.ReportTable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

class PdfReportRendererTest {

  @Test
  void simpleTable_rendersValidSinglePagePdf() throws IOException {
    ReportTable table =
        ReportTable.builder()
            .headers(List.of("status", "count"))
            .rows(List.of(List.of("LISTED", "3"), List.of("SUBMITTED", "5")))
            .build();

    byte[] pdf = PdfReportRenderer.render(table, "loan-pipeline");

    assertThat(pdf).isNotEmpty();
    assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isEqualTo(1);
    }
  }

  @Test
  void manyRows_paginatesAcrossMultiplePages() throws IOException {
    List<List<String>> rows = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      rows.add(List.of("row-" + i, String.valueOf(i)));
    }
    ReportTable table = ReportTable.builder().headers(List.of("label", "value")).rows(rows).build();

    byte[] pdf = PdfReportRenderer.render(table, "big-report");

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isGreaterThan(1);
    }
  }

  @Test
  void emptyRows_stillRendersHeaderOnlyPdf() throws IOException {
    ReportTable table = ReportTable.builder().headers(List.of("a", "b")).rows(List.of()).build();

    byte[] pdf = PdfReportRenderer.render(table, "empty-report");

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isEqualTo(1);
    }
  }
}
