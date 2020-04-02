package com.almworks.export.pdf.itext;

import com.almworks.api.application.Attachment;
import com.almworks.api.application.ModelKey;
import com.almworks.api.download.DownloadManager;
import com.almworks.api.download.DownloadedFile;
import com.almworks.api.engine.Connection;
import com.almworks.util.exec.Context;
import com.almworks.util.files.FileUtil;
import com.almworks.util.properties.PropertyMap;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.almworks.util.Log;

import java.io.*;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Date;

/**
 * @author Alex
 */

public class Attachments extends PrintKeyElement<Collection<Attachment>, Document> {
  private final boolean myPrintText;
  private final boolean myPrintGraphics;
  private DownloadManager myDownloadManager;
  private DateFormat myDateFormat;

  protected Attachments(ModelKey<Collection<Attachment>> collectionModelKey, boolean printText, boolean printGraphics) {
    super(collectionModelKey);
    myPrintText = printText;
    myPrintGraphics = printGraphics;

    myDownloadManager = Context.require(DownloadManager.ROLE);
  }

  public void appendPrintElement(Document container, ReportMetrics metrics, PdfWriter writer) throws DocumentException {
    final Collection<Attachment> data = getData();

    if (data.isEmpty())
      return;

    boolean headerPrinted = false;

    for (final Attachment attachment : data) {
      String mime;
      String filePath;
      String fileName;

      if (!attachment.isLocal()) {
        final DownloadedFile downloadedFile = myDownloadManager.getDownloadStatus(attachment.getUrl());
        if (downloadedFile.getState() != DownloadedFile.State.READY)
          continue;
        File file = downloadedFile.getFile();
        if (file == null)
          continue;
        filePath = file.getAbsolutePath();
        fileName = file.getName();
        mime = downloadedFile.getMimeType();
      } else {
        File file = attachment.getFileForUpload();
        if (file == null)
          continue;
        filePath = file.getAbsolutePath();
        fileName = file.getName();
        mime = attachment.getMimeType();
      }
      assert fileName != null : attachment;
      assert filePath != null : attachment;
      if (mime == null)
        mime = FileUtil.guessMimeType(fileName);
      if (mime == null)
        continue;

      final Font font = metrics.whenWhoFont();

      PdfPTable attachTable = new PdfPTable(2);
      attachTable.setWidthPercentage(100);
      attachTable.setSpacingBefore(metrics.SPACING);
      attachTable.setExtendLastRow(false);
      attachTable.setSplitRows(true);
      attachTable.setSplitLate(false);
      Phrase phrase = new Phrase("", font);

      addLine(phrase, "Attachment #", Integer.toString(attachment.getNumber()));
      addLine(phrase, "  ", fileName);
      addLine(phrase, "  ", attachment.getUser());

      addPhraseCell(attachTable, phrase, Rectangle.ALIGN_LEFT);

      Date d = attachment.getDate();
      final String date = d == null ? "" : myDateFormat.format(d);
      phrase = new Phrase("", font);
      addLine(phrase, "", date);
      addPhraseCell(attachTable, phrase, Rectangle.ALIGN_RIGHT);

      boolean print = false;
      if (isGraphics(mime) && myPrintGraphics) {
        addContentLine(attachTable, printImage(container, filePath));
        print = true;
      } else if (isText(mime) && myPrintText) {
        addContentLine(attachTable, printTextAttachment(metrics, attachment, filePath));
        print = true;
      }
      if (print) {
        if (!headerPrinted) {
          headerPrinted = true;
          final Paragraph paragraph = new Paragraph("Attachments", metrics.keyReportFont());
          paragraph.setSpacingBefore(metrics.SPACING);
          container.add(paragraph);
        }
        container.add(attachTable);
      }
    }
  }

  private void addPhraseCell(PdfPTable attachTable, Phrase phrase, int hAlign) {
    PdfPCell cell = new PdfPCell(phrase);
    cell.setPadding(0);
    cell.setVerticalAlignment(Rectangle.ALIGN_BASELINE);
    cell.setHorizontalAlignment(hAlign);
    cell.setUseAscender(true);
    cell.setBorder(0);
    attachTable.addCell(cell);
  }

  private Paragraph printTextAttachment(ReportMetrics metrics, Attachment attachment, String filename)
    throws DocumentException
  {
    File textFile = new File(filename);
    try {

      int expectedSize = (int) attachment.getExpectedSize();
      StringBuilder sb = expectedSize <=0 ? new StringBuilder() : new StringBuilder(expectedSize);
      int len;
      char[] buffer = new char[1024];

      Reader readstream = new FileReader(textFile);
      try {
        while ((len = readstream.read(buffer)) > -1) {
          sb.append(buffer, 0, len);
        }
      } finally {
        readstream.close();
      }

      final Paragraph paragraph = new Paragraph(metrics.attachFont().getSize());
      paragraph.setAlignment(Paragraph.ALIGN_LEFT);
      paragraph.setFirstLineIndent(0);
      paragraph.setSpacingBefore(3);
      paragraph.setSpacingAfter(3);

      paragraph.add(new Phrase(metrics.attachFont().getSize(), sb.toString(), metrics.attachFont()));

      return paragraph;
    } catch (IOException e) {
      Log.error("Can't open file " + filename + "(" + e + ")");
      return null;
    }
  }

  private Image printImage(Document document, String filename) throws DocumentException {
    try {
      Image image = Image.getInstance(filename);

      image.scalePercent(40, 40);
      image.setInterpolation(true);
      final Rectangle pageSize = document.getPageSize();

      float prWidth = document.right() - document.left();
      float prHeight = document.top() - document.bottom();

      if (image.getScaledWidth() > prWidth || image.getScaledHeight() > prHeight) {
        image.scaleToFit(prWidth, prHeight);
      }

      return image;
    } catch (IOException e) {
      Log.warn("can't load image " + filename);
    }
    return null;
  }

  private void addLine(Phrase phrase, String key, String value) {
    if (value == null || value.equals(""))
      return;
    phrase.add(new Chunk(key + value + " "));
  }

  private void addContentLine(PdfPTable attachTable, Image image) {
    if (image == null)
      return;
    PdfPCell cell = new PdfPCell(image);
    cell.setPadding(8f);
    cell.setColspan(2);
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
    cell.setBorder(0);
    attachTable.addCell(cell);
  }

  private void addContentLine(PdfPTable attachTable, Paragraph text) {
    if (text == null)
      return;
    PdfPCell cell = new PdfPCell(text);
    cell.setColspan(2);
    cell.setBorder(0);
    cell.setHorizontalAlignment(Element.ALIGN_LEFT);
    attachTable.addCell(cell);
  }

  private boolean isText(String mime) {
    return mime.equals("text/plain") || mime.contains("text") || mime.contains("html") || mime.contains("xml");
  }

  private boolean isGraphics(String mime) {
    return mime.startsWith("image") &&
      (mime.endsWith("jpg") || mime.endsWith("jpeg") || mime.endsWith("gif") || mime.endsWith("png"));
  }

  public void setContext(Connection connection, PropertyMap propertyMap, NumberFormat numberFormat, DateFormat dateFormat, boolean isHtml) {
    super.setContext(connection, propertyMap, numberFormat, dateFormat, isHtml);
    myDateFormat = dateFormat;
  }
}
