package com.almworks.export.pdf.itext;

import com.almworks.api.application.ModelKey;
import com.almworks.api.application.viewer.Comment;
import com.almworks.api.engine.Connection;
import com.almworks.util.properties.PropertyMap;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Date;

public class Comments extends PrintKeyElement<Collection<Comment>, Document> {
  private DateFormat myDataFormat;

  protected Comments(ModelKey<Collection<Comment>> collectionModelKey) {
    super(collectionModelKey);
  }

  public void setContext(Connection connection, PropertyMap propertyMap, NumberFormat numberFormat, DateFormat dateFormat, boolean isHtml) {
    myDataFormat = dateFormat;
    super.setContext(connection, propertyMap, numberFormat, dateFormat, isHtml);
  }

  public void appendPrintElement(Document container, ReportMetrics metrics, PdfWriter writer) throws DocumentException {

    for (Comment commentData : getData()) {
      if (!commentData.getText().equals("") ) {
        final Paragraph paragraph =
          new Paragraph("Comments", metrics.keyReportFont());
        paragraph.setSpacingBefore(metrics.SPACING);
        paragraph.setSpacingAfter(metrics.SPACING / 2);
        container.add(paragraph);
        break;
      }
      return;
    }

    int count = 1;

    PdfPTable commentsTable = new PdfPTable(1);

    commentsTable.setSplitRows(true);
    commentsTable.setSplitLate(false);
    commentsTable.setSpacingAfter(metrics.SPACING);

    commentsTable.setWidthPercentage(100);

    for (Comment commentData : getData()) {
      if (commentData.getText().equals("") ) {
        continue;
      }
      PdfPTable comment = new PdfPTable(2);
      comment.setSplitRows(true);
      comment.setSplitLate(false);

      final String who = String.format("#%d %s", count++, commentData.getWhoText());
      PdfPCell c = new PdfPCell(new Paragraph(who, metrics.whenWhoFont()));

      c.setBorder(0);
      comment.addCell(c);

      final Date date = commentData.getWhen();
      c = new PdfPCell(new Paragraph(date != null ? myDataFormat.format(date) : commentData.getWhenText(),
        metrics.whenWhoFont()));
      c.setBorder(0);
      c.setHorizontalAlignment(Element.ALIGN_RIGHT);

      comment.addCell(c);
      final Paragraph paragraph = new Paragraph(metrics.commentDescriptionFont().getSize() * 1.2f, commentData.getText(),
        metrics.commentDescriptionFont());
      c = new PdfPCell(paragraph);


      c.setBorder(0);
      c.setColspan(2);

      comment.addCell(c);
      PdfPCell p = new PdfPCell(comment);

      p.setPaddingTop(metrics.BEETWEEN_COMMENT_SPACING);

      p.setBorder(0);
      p.setBorderWidth(0);


      commentsTable.addCell(p);
    }
    container.add(commentsTable);
  }
}
