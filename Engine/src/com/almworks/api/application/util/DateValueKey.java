package com.almworks.api.application.util;

import com.almworks.api.application.ModelMergePolicy;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.properties.PropertyMap;

import java.text.DateFormat;
import java.util.Comparator;
import java.util.Date;

public class DateValueKey extends ValueKey<Date> implements CanvasRenderer<PropertyMap> {
  private final ModelMergePolicy myMergePolicy;
  private static final DateFormat GENERAL_DATE_FORMAT = DateUtil.LOCAL_DATE;
  private static final DateFormat TIME_FORMAT = DateUtil.US_HOURS_MINUTES;

  public DateValueKey(DBAttribute<Date> attribute, Comparator<Date> comparator, boolean exportAsString, String displayableName, ModelMergePolicy mergePolicy) {
    super(attribute, comparator, exportAsString, displayableName);
    myMergePolicy = mergePolicy;
    setRenderer(this);
  }

  public ModelMergePolicy getMergePolicy() {
    return myMergePolicy;
  }

  public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
    Date date = getValue(item);
    if (date == null)
      return;
    Date temp = new Date(date.getTime());
    canvas.appendText(GENERAL_DATE_FORMAT.format(temp) + " " + TIME_FORMAT.format(temp));
  }
}
