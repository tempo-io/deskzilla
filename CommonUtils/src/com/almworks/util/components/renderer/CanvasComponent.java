package com.almworks.util.components.renderer;

import javax.swing.*;
import java.awt.*;
import java.util.regex.Pattern;

/**
 * @author dyoma
 */
public interface CanvasComponent {
  JComponent getComponent();

  void setCurrentElement(CanvasElement element);

  void setComponentFont(Font font);

  Font getDerivedFont(int fontStyle);

  void clear();

  Pattern getHighlightPattern();
}
