package com.almworks.util.ui.widgets.util;

import com.almworks.util.ui.widgets.CellContext;
import com.almworks.util.ui.widgets.GraphContext;
import org.jetbrains.annotations.*;

import java.awt.*;

public class GapWidget extends LeafRectCell<Object> {
  private final Dimension mySize;

  public GapWidget(Dimension size) {
    mySize = size;
  }

  @Override
  protected Dimension getPrefSize(CellContext context, Object value) {
    return mySize;
  }

  @Override
  public void paint(@NotNull GraphContext context, @Nullable Object value) {
  }
}
