package com.lendbridge.loan.document;

import com.lendbridge.loan.model.LoanApplication;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Renders a simple, versioned loan-terms agreement PDF with Apache PDFBox — a plain text layout,
 * not an HTML/template-engine rendering stack, since the agreement in Phase 1 only needs to state
 * the loan terms (amount, rate, tenor), not borrower PII pulled from user-profile-service (left for
 * a future enhancement — see docs/adr/0015-loan-service-phase-1-scope.md).
 */
@Component
public class AgreementPdfRenderer {

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

  private final int templateVersion;

  public AgreementPdfRenderer(@Value("${loan.agreement.template-version}") int templateVersion) {
    this.templateVersion = templateVersion;
  }

  public int templateVersion() {
    return templateVersion;
  }

  public byte[] render(LoanApplication loan) {
    try (PDDocument document = new PDDocument()) {
      PDPage page = new PDPage();
      document.addPage(page);

      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        var titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        var bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float y = 740;
        float left = 60;

        content.beginText();
        content.setFont(titleFont, 16);
        content.newLineAtOffset(left, y);
        content.showText("LendBridge Loan Agreement");
        content.endText();
        y -= 30;

        for (String line : termsLines(loan)) {
          content.beginText();
          content.setFont(bodyFont, 11);
          content.newLineAtOffset(left, y);
          content.showText(line);
          content.endText();
          y -= 20;
        }
      }

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to render agreement PDF for loan " + loan.getId(), e);
    }
  }

  private String[] termsLines(LoanApplication loan) {
    BigDecimal annualRatePercent =
        BigDecimal.valueOf(loan.getInterestRateBps()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
    BigDecimal amountNaira = BigDecimal.valueOf(loan.getAmountKobo()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_EVEN);
    return new String[] {
      "Template version: " + templateVersion,
      "Loan reference: " + loan.getId(),
      "Borrower: " + loan.getBorrowerUserId(),
      "Principal amount: NGN " + amountNaira,
      "Tenor: " + loan.getTenorMonths() + " months",
      "Annual interest rate: " + annualRatePercent + "%",
      "Purpose: " + loan.getPurpose() + (loan.getPurposeDetail() == null ? "" : " - " + loan.getPurposeDetail()),
      "Generated: " + TIMESTAMP_FORMAT.format(java.time.Instant.now()),
      "",
      "This document states the loan terms as listed on the LendBridge marketplace. It does not",
      "yet constitute a signed agreement — e-signature acceptance is introduced once lender",
      "funding commitments exist (see docs/adr/0015-loan-service-phase-1-scope.md)."
    };
  }
}
