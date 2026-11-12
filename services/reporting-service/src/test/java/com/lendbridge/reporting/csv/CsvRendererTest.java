package com.lendbridge.reporting.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.lendbridge.reporting.dto.ReportTable;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvRendererTest {

  @Test
  void simpleTable_rendersHeaderAndRows() {
    ReportTable table =
        ReportTable.builder()
            .headers(List.of("status", "count"))
            .rows(List.of(List.of("LISTED", "3"), List.of("SUBMITTED", "5")))
            .build();

    String csv = CsvRenderer.render(table);

    assertThat(csv).isEqualTo("status,count\r\nLISTED,3\r\nSUBMITTED,5\r\n");
  }

  @Test
  void fieldContainingComma_isQuoted() {
    ReportTable table =
        ReportTable.builder().headers(List.of("a", "b")).rows(List.of(List.of("x,y", "z"))).build();

    assertThat(CsvRenderer.render(table)).isEqualTo("a,b\r\n\"x,y\",z\r\n");
  }

  @Test
  void fieldContainingQuote_isEscapedByDoubling() {
    ReportTable table =
        ReportTable.builder().headers(List.of("a")).rows(List.of(List.of("say \"hi\""))).build();

    assertThat(CsvRenderer.render(table)).isEqualTo("a\r\n\"say \"\"hi\"\"\"\r\n");
  }

  @Test
  void fieldContainingNewline_isQuoted() {
    ReportTable table =
        ReportTable.builder().headers(List.of("a")).rows(List.of(List.of("line1\nline2"))).build();

    assertThat(CsvRenderer.render(table)).isEqualTo("a\r\n\"line1\nline2\"\r\n");
  }

  @Test
  void nullCell_rendersAsEmptyString() {
    ReportTable table = ReportTable.builder().headers(List.of("a")).rows(List.of(java.util.Collections.singletonList(null))).build();

    assertThat(CsvRenderer.render(table)).isEqualTo("a\r\n\r\n");
  }

  @Test
  void emptyRows_rendersHeaderOnly() {
    ReportTable table = ReportTable.builder().headers(List.of("a", "b")).rows(List.of()).build();

    assertThat(CsvRenderer.render(table)).isEqualTo("a,b\r\n");
  }
}
