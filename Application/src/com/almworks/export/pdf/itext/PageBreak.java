package com.almworks.export.pdf.itext;

import com.almworks.api.engine.Connection;
import com.almworks.util.properties.PropertyMap;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;

import java.text.DateFormat;
import java.text.NumberFormat;

/**
 * @author Alex
 */

public class PageBreak implements PrintElement<Document> {
  private final boolean myFromBlank;

  public PageBreak(boolean fromBlank) {
    myFromBlank = fromBlank;
  }

  public void setContext(Connection connection, PropertyMap propertyMap, NumberFormat numberFormat, DateFormat dateFormat, boolean isHtml) {
  }

  public void appendPrintElement(Document container, ReportMetrics metrics, PdfWriter writer) throws DocumentException {
    if (myFromBlank) {
      final Chunk chunk = new Chunk(" ");
      chunk.setGenericTag("issue_end");
      Paragraph par = new Paragraph(chunk);
      container.add(par);
      container.newPage();
    } else {
      final Chunk chunk = new Chunk(" ");
      chunk.setGenericTag("issue_end_break");
      Paragraph par = new Paragraph();
      par.add(chunk);
      ITextUtil.setSpacing(par, metrics.PAGE_BREAK_SPACING);
      container.add(par);
    }
  }
}
