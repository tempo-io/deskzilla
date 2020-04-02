package com.almworks.util.ui.widgets.impl;

import javax.swing.*;
import java.awt.*;

public interface IWidgetHostComponent {
  void setCursor(Cursor cursor);

  void repaint(int x, int y, int width, int height);

  void updateAll();

  JComponent getHostComponent();

  void fullRefresh();

  void repaintAll();

  int getWidth();

  int getHeight();

  Color getSelectionBg();

  void setRemovingComponent(Component component);

  void revalidate();

  void widgetRequestsFocus();
}
