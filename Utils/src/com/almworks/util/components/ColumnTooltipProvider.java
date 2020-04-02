package com.almworks.util.components;

import com.almworks.util.components.renderer.CellState;
import org.jetbrains.annotations.*;

import java.awt.*;

public interface ColumnTooltipProvider<T> {
  @Nullable
  String getTooltip(CellState cellState, T element, Point cellPoint, Rectangle cellRect);
}
