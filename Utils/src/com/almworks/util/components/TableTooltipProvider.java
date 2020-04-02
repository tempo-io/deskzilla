package com.almworks.util.components;

import org.jetbrains.annotations.*;

import java.awt.*;

public interface TableTooltipProvider {
  @Nullable
  String getTooltip(int row, int column, Point tablePoint);
}
