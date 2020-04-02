package com.almworks.export.pdf.itext;

import com.almworks.api.engine.Connection;
import com.almworks.util.properties.PropertyMap;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfWriter;

import java.text.DateFormat;
import java.text.NumberFormat;

/**
 * @author Alex
 */
public interface PrintElement<D> {
  void setContext(Connection connection, PropertyMap propertyMap, NumberFormat numberFormat, DateFormat dateFormat,
    boolean isHtml);
  void appendPrintElement(D container, ReportMetrics metrics, PdfWriter writer) throws DocumentException;
}
