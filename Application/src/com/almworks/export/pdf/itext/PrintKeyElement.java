package com.almworks.export.pdf.itext;

import com.almworks.api.application.ModelKey;
import com.almworks.api.engine.Connection;
import com.almworks.util.properties.PropertyMap;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;

import java.text.DateFormat;
import java.text.NumberFormat;

/**
 * @author Alex
 */
public abstract class PrintKeyElement<P, D> implements PrintElement<D> {
  private final ModelKey<P> myModelKey;

  private P myData;

  public PrintKeyElement(ModelKey<P> modelKey) {
    assert modelKey != null;
    myModelKey = modelKey;
  }

  public ModelKey<P> getModelKey() {
    return myModelKey;
  }

  public void setContext(Connection connection, PropertyMap propertyMap, NumberFormat numberFormat, DateFormat dateFormat, boolean isHtml) {
    setData(myModelKey.getValue(propertyMap));
  }

  public void setData(P data) {
    myData = data;
  }

  public P getData() {
    return myData;
  }


  public static class PhrasePrintKeyElement<T> extends ExportedPrintElement<T, Phrase> {

    protected PhrasePrintKeyElement(ModelKey<T> tModelKey) {
      super(tModelKey);
    }

    public void appendPrintElement(Phrase container, ReportMetrics metrics, PdfWriter writer) throws DocumentException {
      if (getExportText() != null) {
        final Chunk o = new Chunk(getExportText() + " ");
        container.add(o);
      }
    }
  }
}
