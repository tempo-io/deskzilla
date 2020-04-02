package com.almworks.export.pdf.itext;

import com.almworks.api.application.ExportValueType;
import com.almworks.api.application.ModelKey;
import com.almworks.api.engine.Connection;
import com.almworks.util.Pair;
import com.almworks.util.properties.PropertyMap;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Collections;

/**
 * @author Alex
 */

public abstract class ExportedPrintElement<T, D> extends PrintKeyElement<T, D>{

  private String myExportText;

  public ExportedPrintElement(ModelKey<T> tModelKey) {
    super(tModelKey);
  }

  public String getExportText() {
    return myExportText;
  }

  public void setContext(Connection connection, PropertyMap propertyMap, NumberFormat numberFormat, DateFormat dateFormat, boolean isHtml) {
    if (getModelKey().isExportable(Collections.singletonList(connection))) {
      final Pair<String,ExportValueType> stringExportValueTypePair =
        getModelKey().formatForExport(propertyMap, numberFormat, dateFormat, isHtml);
      if (stringExportValueTypePair != null) {
        myExportText = stringExportValueTypePair.getFirst();
      }
    } else {
      myExportText = null;
    }
  }

}
