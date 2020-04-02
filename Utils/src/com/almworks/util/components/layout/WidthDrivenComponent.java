package com.almworks.util.components.layout;

import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * @author dyoma
 */
public interface WidthDrivenComponent {
  int getPreferredWidth();

  int getPreferredHeight(int width);

  @NotNull
  JComponent getComponent();

  boolean isVisibleComponent();
}
