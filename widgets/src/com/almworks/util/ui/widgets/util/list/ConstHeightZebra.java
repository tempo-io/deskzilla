package com.almworks.util.ui.widgets.util.list;

import com.almworks.util.advmodel.AListModel;
import com.almworks.util.ui.ColorUtil;
import com.almworks.util.ui.widgets.*;
import org.almworks.util.TypedKey;

import javax.swing.*;
import java.awt.*;

public class ConstHeightZebra implements TableLayoutPolicy, TablePaintPolicy {
  private static final TypedKey<Integer> RAW_CELL_HEIGHT = TypedKey.create("rawCellHeight");
  private final int myTopInset;
  private final int myBottomInset;

  public ConstHeightZebra(int topInset, int bottomInset) {
    myTopInset = topInset;
    myBottomInset = bottomInset;
  }

  @Override
  public void paintTable(GraphContext context, ColumnListWidget.CellState state) {
    Integer h = context.getStateValue(RAW_CELL_HEIGHT);
    if (h == null) return;
    int height = h + myBottomInset + myTopInset;
    Rectangle clip = context.getLocalClip(null);
    int y = 0;
    JComponent host = context.getHost().getHostComponent();
    context.setColor(ColorUtil.getStripeBackground(host.getBackground()));
    int i = 0;
    while (true) {
      if ((i %2 == 1) && (y + height >= clip.y))
        context.fillRect(0, y, context.getWidth(), height);
      y += height;
      if (y > clip.y + clip.height) break;
      i++;
    }
  }
  
  @Override
  public int[] getMargin(int[] rowHeights, int rowIndex, int[] target) {
    return getMargin(rowHeights.length, rowIndex, target);
  }

  private int[] getMargin(int rowCount, int rowIndex, int[] target) {
    if (target == null) target = new int[2];
    target[0] = myTopInset;
    target[1] = myBottomInset;
    return target;
  }

  @Override
  public <T> int getPreferedCellHeight(CellContext context, ColumnListWidget<T> listWidget, int rowIndex, T rowValue,
    int[] widths, AListModel<T> model) {
    int height = getCachedPrefHeight(context, listWidget, rowIndex, rowValue, widths);
    int[] margin = getMargin(model.getSize(), rowIndex, null);
    return height + margin[0] + margin[1];
  }

  private <T> int getCachedPrefHeight(CellContext context, ColumnListWidget<T> listWidget, int rowIndex, T rowValue, int[] widths) {
    Integer height = context.getStateValue(RAW_CELL_HEIGHT);
    if (height == null) {
      height = calcPrefHeight(context, listWidget, rowIndex, rowValue, widths);
      context.putStateValue(RAW_CELL_HEIGHT, height, true);
    }
    if (height == null) return 0;
    return height;
  }

  private <T> int calcPrefHeight(CellContext context, ColumnListWidget<T> listWidget, int rowIndex, T rowValue, int[] widths) {
    int result = 0;
    for (int c = 0; c < listWidget.getColumnCount(); c++) {
      Widget<T> columnWidget = listWidget.getColumnWidget(c);
      int h = context.getChildPreferedHeight(listWidget.getCellId(c, rowIndex), columnWidget, rowValue, widths[c]);
      result = Math.max(result, h);
    }
    return result;
  }

  @Override
  public void invalidateLayoutCache(CellContext context) {
    context.putStateValue(RAW_CELL_HEIGHT, null, true);
  }

  private int getVerticalInset() {
    return myTopInset + myBottomInset;
  }
}
