package com.almworks.export.pdf.itext;

import com.almworks.api.application.ModelKey;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;

/**
 * @author Alex
 */

public class LargeTextElement<T> extends ExportedPrintElement<T, Document> {

  protected LargeTextElement(ModelKey<T> hostedStringModelKey) {
    super(hostedStringModelKey);
  }

  public void appendPrintElement(Document container, ReportMetrics metrics, PdfWriter writer) throws DocumentException {
    if (!getExportText().equals("")) {
      final float fontSize = metrics.keyReportFont().getSize();
      Paragraph paragraph = new Paragraph(getModelKey().getDisplayableName(), metrics.keyReportFont());

      paragraph.setSpacingBefore(metrics.SPACING);
      paragraph.setSpacingAfter(fontSize / 3);
      container.add(paragraph);
      paragraph =
        new Paragraph(metrics.BASIC_TEXT_LEADING, getExportText(), metrics.commentDescriptionFont());
      paragraph.setSpacingAfter(metrics.SPACING);

      container.add(paragraph);
    }
  }
}
