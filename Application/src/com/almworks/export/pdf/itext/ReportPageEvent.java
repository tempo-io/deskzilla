package com.almworks.export.pdf.itext;

import com.almworks.api.platform.ProductDistributionType;
import com.almworks.api.platform.ProductInformation;
import com.almworks.export.ExportParameters;
import com.almworks.export.ExportedData;
import com.almworks.util.datetime.DateUtil;
import com.lowagie.text.Document;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;

import java.awt.*;

/**
 * @author Alex
 */

public class ReportPageEvent extends PdfPageEventHelper {
  protected PdfTemplate pageNumFooter;
  private final ReportMetrics myReportMetrics;
  private ExportedData myData;
  private BaseFont myBaseFont;
  String myCurrent = "";
  public static final int FONT_SIZE = 10;
  public static final int DISTR_INFO_FONT_SIZE = 8;

  private PdfGState myGstate;
  private Color myWaterColor = Color.LIGHT_GRAY;
  private final ProductInformation myProductInfo;

  public ReportPageEvent(ReportMetrics reportMetrics, ExportedData data, ExportParameters parameters,
    ProductInformation productInfo) {
    myReportMetrics = reportMetrics;
    myData = data;
    myProductInfo = productInfo;
    myBaseFont = myReportMetrics.attribureValueFont(true).getBaseFont();
  }

  public void onOpenDocument(PdfWriter writer, Document document) {
    pageNumFooter = writer.getDirectContent().createTemplate(100, 100);
    pageNumFooter.setBoundingBox(new Rectangle(-20, -20, 100, 100));

    myGstate = new PdfGState();
    myGstate.setFillOpacity(0.1f);
    myGstate.setStrokeOpacity(0.1f);

  }

  public void onStartPage(PdfWriter writer, Document document) {
    if (!myCurrent.equals("")) {
      PdfContentByte cb = writer.getDirectContent();
      cb.saveState();
      cb.beginText();
      cb.setFontAndSize(myBaseFont, FONT_SIZE);
      cb.setTextMatrix(document.left(), document.top() + FONT_SIZE);

      String name = myData.getCollectionName();
      final float v = countWidth(name);
      float totalSize = document.right() - document.left() - v;

      while (countWidth(myCurrent) > totalSize) {
        myCurrent = myCurrent.substring(0, myCurrent.length() / 3 * 2);
        myCurrent += "\u2026";
      }

      cb.showText(myCurrent);
      cb.endText();
      cb.restoreState();
    }
  }

  public void onGenericTag(PdfWriter writer, Document document, Rectangle rect, String text) {
    if (text.startsWith("issue_end")) {
      if (text.endsWith("break")){
        PdfContentByte cb = writer.getDirectContentUnder();
        cb.setRGBColorStroke(0x50, 0x50, 0x50);
        cb.setLineWidth(1.5f);
        float v = (rect.getTop() + rect.getBottom()) / 2;
        cb.moveTo(document.left(), v);
        cb.lineTo(document.right(), v);

        cb.stroke();
        cb.resetRGBColorStroke();
      }
      myCurrent = "";

    } else if (text.startsWith("issue")) {
      PdfContentByte cb = writer.getDirectContent();

      PdfDestination destination =
        new PdfDestination(PdfDestination.FITH, rect.getTop());

      myCurrent = text.substring(5);
      PdfOutline outline =
        new PdfOutline(cb.getRootOutline(), destination, myCurrent);
    }
  }

  public void onEndPage(PdfWriter writer, Document document) {

    PdfContentByte cb = writer.getDirectContent();
    cb.saveState();

    cb.beginText();
    cb.setFontAndSize(myBaseFont, FONT_SIZE);

    String name = myData.getCollectionName();
    cb.setTextMatrix(document.right() - countWidth(name), document.top()+ FONT_SIZE);
    cb.showText(name);

    float footerH = 20f;
    float textBase = document.bottom() - footerH;

    writeDistrInfo(cb, document, textBase);

    String date = DateUtil.toLocalDateTime(myData.getDateCollected());
    cb.setTextMatrix(document.right() - countWidth(date), textBase);
    cb.showText(date);

    String text = "Page " + writer.getPageNumber() + " of ";
    float textSize = countWidth(text);
    float adjust = countWidth("0");
    cb.setTextMatrix((document.right() - textSize - adjust) / 2, textBase);
    cb.showText(text);
    cb.endText();

    cb.addTemplate(pageNumFooter, (document.right() - adjust + textSize) / 2, textBase);


    cb.restoreState();
  }

  private void writeDistrInfo(PdfContentByte cb, Document document, float textBase) {
    if (myProductInfo.getDistributionType() != ProductDistributionType.FULL) {
      String info = "Exported by " + myProductInfo.getName();
      cb.setTextMatrix(document.left(), textBase);
      cb.showText(info);
    }
  }

  private float countWidth(String info) {
    return myBaseFont.getWidthPoint(info, FONT_SIZE);
  }

  public void onCloseDocument(PdfWriter writer, Document document) {
    pageNumFooter.beginText();
    pageNumFooter.setFontAndSize(myBaseFont, FONT_SIZE);
    pageNumFooter.setTextMatrix(0, 0);
    pageNumFooter.showText(String.valueOf(writer.getPageNumber() - 1));
    pageNumFooter.endText();
  }
}
