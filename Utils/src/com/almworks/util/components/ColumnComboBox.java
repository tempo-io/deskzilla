package com.almworks.util.components;

import com.almworks.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author dyoma
 */
public class ColumnComboBox extends JComboBox {
  private int myColumns = 10;

  public Dimension getMinimumSize() {
    Dimension minimumSize = super.getMinimumSize();
    if (myColumns > 0) {
      int preferredWidth = getPreferredWidth();
      if (minimumSize.width > preferredWidth)
        minimumSize = new Dimension(preferredWidth, minimumSize.height);
    }
    return minimumSize;
  }

  public Dimension getPreferredSize() {
    Dimension preferredSize = super.getPreferredSize();
    if (myColumns > 0)
      preferredSize = new Dimension(getPreferredWidth(), preferredSize.height);
    return preferredSize;
  }

  private int getPreferredWidth() {
    return UIUtil.getColumnWidth(this) * myColumns;
  }

  public int getColumns() {
    return myColumns;
  }

  public void setColumns(int columns) {
    if (columns != myColumns) {
      myColumns = columns;
      invalidate();
    }
  }
}
