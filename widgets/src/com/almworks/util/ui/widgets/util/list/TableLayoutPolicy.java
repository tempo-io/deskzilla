package com.almworks.util.ui.widgets.util.list;

import com.almworks.util.advmodel.AListModel;
import com.almworks.util.ui.widgets.CellContext;
import org.jetbrains.annotations.*;

public interface TableLayoutPolicy {
  @NotNull
  int[] getMargin(int[] rowHeights, int rowIndex, @Nullable int[] target);

  <T> int getPreferedCellHeight(CellContext context, ColumnListWidget<T> listWidget, int rowIndex, T rowValue, int[] width,
    AListModel<T> model);

  void invalidateLayoutCache(CellContext context);
}
