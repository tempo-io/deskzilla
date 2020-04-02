package com.almworks.bugzilla.provider.datalink.flags2.columns;

import com.almworks.bugzilla.provider.datalink.flags2.*;
import com.almworks.util.commons.Condition;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.CanvasRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;

import java.util.Collections;
import java.util.List;

class AggregateRenderer implements CanvasRenderer<PropertyMap> {
  private final Condition<FlagVersion> myFilter;
  private final boolean myShowStatus;
  static final AggregateRenderer ALL = new AggregateRenderer(FlagStatus.UNKNOWN);
  static final AggregateRenderer PLUS = new AggregateRenderer(FlagStatus.PLUS);
  static final AggregateRenderer MINUS = new AggregateRenderer(FlagStatus.MINUS);
  static final AggregateRenderer QUESTION = new AggregateRenderer(FlagStatus.QUESTION);

  AggregateRenderer(FlagStatus status) {
    myFilter = createFilter(status);
    myShowStatus = status == FlagStatus.UNKNOWN;
  }

  private Condition<FlagVersion> createFilter(final FlagStatus status) {
    if (status == FlagStatus.UNKNOWN) return Condition.always();
    return new Condition<FlagVersion>() {
      @Override
      public boolean isAccepted(FlagVersion value) {
        return value.getStatus() == status;
      }
    };
  }

  @Override
  public void renderStateOn(CellState state, Canvas canvas, PropertyMap item) {
    List<FlagVersion> resolvedFlags = FlagsModelKey.getAllFlags(item, false);
    resolvedFlags = myFilter.filterList(resolvedFlags);
    Collections.sort(resolvedFlags, Flag.ORDER);
    showMultipleFlags(canvas, resolvedFlags);
  }


  private void showMultipleFlags(Canvas canvas, List<FlagVersion> resolvedFlags) {
    String separator = "";
    for (FlagVersion flag : resolvedFlags) {
      canvas.appendText(separator);
      String displayString = flag.getName();
      if (myShowStatus) {
        displayString += flag.getStatus().getDisplayPresentation();
      }
      FlagsRenderer.appendText(canvas, displayString, flag.isThisUserRequested());
      separator = "  ";
    }
  }
}
