package com.lendbridge.reporting.export;

import com.lendbridge.reporting.dto.ReportTable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Renders a {@link ReportTable} as a simple positioned-text table PDF with Apache PDFBox — same
 * plain-text layout style as loan-service's {@code AgreementPdfRenderer}, not an
 * HTML/template-engine stack. Shared by every report endpoint (generic on {@code ReportTable}),
 * same spirit as {@link CsvRenderer} being one renderer rather than one per report.
 *
 * <p>Paginates once content runs past the page bottom, re-drawing the header row on every new
 * page — a report page is often read standalone (printed, or opened straight into a PDF viewer at
 * an arbitrary page), so repeating the header is more useful here than in a single-page document.
 */
public final class PdfReportRenderer {

  private static final float LEFT_MARGIN = 50;
  private static final float TOP_START_Y = 740;
  private static final float BOTTOM_MARGIN = 60;
  private static final float ROW_HEIGHT = 18;
  private static final float TITLE_FONT_SIZE = 16;
  private static final float HEADER_FONT_SIZE = 10;
  private static final float BODY_FONT_SIZE = 9;

  private PdfReportRenderer() {}

  public static byte[] render(ReportTable table, String title) {
    try (PDDocument document = new PDDocument()) {
      PDPage firstPage = new PDPage();
      document.addPage(firstPage);
      float pageWidth = firstPage.getMediaBox().getWidth();
      float columnWidth = (pageWidth - 2 * LEFT_MARGIN) / Math.max(1, table.getHeaders().size());

      var titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      var headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      var bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

      PageCursor cursor = new PageCursor(document, firstPage);
      cursor.open();
      cursor.drawText(titleFont, TITLE_FONT_SIZE, LEFT_MARGIN, cursor.y, title);
      cursor.y -= 2 * ROW_HEIGHT;
      drawRow(cursor, headerFont, HEADER_FONT_SIZE, columnWidth, table.getHeaders());
      cursor.y -= ROW_HEIGHT;

      for (List<String> row : table.getRows()) {
        if (cursor.y < BOTTOM_MARGIN) {
          cursor.close();
          PDPage nextPage = new PDPage();
          document.addPage(nextPage);
          cursor = new PageCursor(document, nextPage);
          cursor.open();
          drawRow(cursor, headerFont, HEADER_FONT_SIZE, columnWidth, table.getHeaders());
          cursor.y -= ROW_HEIGHT;
        }
        drawRow(cursor, bodyFont, BODY_FONT_SIZE, columnWidth, row);
        cursor.y -= ROW_HEIGHT;
      }
      cursor.close();

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to render report PDF", e);
    }
  }

  private static void drawRow(
      PageCursor cursor, PDType1Font font, float fontSize, float columnWidth, List<String> cells) {
    for (int i = 0; i < cells.size(); i++) {
      String value = cells.get(i) == null ? "" : cells.get(i);
      cursor.drawText(font, fontSize, LEFT_MARGIN + i * columnWidth, cursor.y, value);
    }
  }

  /** Bundles the current page's open content stream with its running y-cursor, so pagination can
   * close one page's stream and open the next without threading extra state through every call. */
  private static final class PageCursor implements AutoCloseable {
    private final PDDocument document;
    private final PDPage page;
    private PDPageContentStream content;
    private float y = TOP_START_Y;

    PageCursor(PDDocument document, PDPage page) {
      this.document = document;
      this.page = page;
    }

    void open() throws IOException {
      content = new PDPageContentStream(document, page);
    }

    void drawText(PDType1Font font, float fontSize, float x, float y, String text) {
      try {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(sanitize(text));
        content.endText();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to write PDF text", e);
      }
    }

    /** PDFBox's standard 14 fonts only support WinAnsiEncoding — strip anything outside it rather
     * than let an unencodable character (e.g. a stray unicode symbol in report data) blow up the
     * whole render. */
    private static String sanitize(String text) {
      StringBuilder sanitized = new StringBuilder(text.length());
      for (char c : text.toCharArray()) {
        sanitized.append(c < 256 ? c : '?');
      }
      return sanitized.toString();
    }

    @Override
    public void close() throws IOException {
      content.close();
    }
  }
}
