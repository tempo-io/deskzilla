package com.almworks.util.components;

import com.almworks.util.components.renderer.CellState;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import javax.swing.*;

/**
 * @author : Dyoma
 */
public interface CanvasRenderable {
  void renderOn(Canvas canvas, CellState state);

  CanvasRenderable EMPTY = new CanvasRenderable() {
    public void renderOn(Canvas canvas, CellState state) {
    }
  };

  abstract class TextWithIcon implements CanvasRenderable {
    private Icon myOpenIcon;
    private Icon myClosedIcon;

    public TextWithIcon(Icon openIcon, Icon closedIcon) {
      myOpenIcon = openIcon;
      myClosedIcon = closedIcon;
    }

    public void renderOn(Canvas canvas, CellState state) {
      canvas.setIcon(state.isExpanded() ? myOpenIcon : myClosedIcon);
      canvas.appendText(getText());
    }

    public boolean setIcon(Icon open, Icon closed) {
      if (Util.equals(myOpenIcon, open) && Util.equals(myClosedIcon, closed))
        return false;
      myOpenIcon = open;
      myClosedIcon = closed;
      return true;
    }

    public Icon getOpenIcon() {
      return myOpenIcon;
    }

    public boolean setIcon(Icon icon) {
      return setIcon(icon, icon);
    }

    @NotNull
    public abstract String getText();
  }

  class FixedText extends TextWithIcon {
    private final String myText;

    public FixedText(String text, Icon openIcon, Icon closedIcon) {
      super(openIcon, closedIcon);
      myText = text;
    }

    public FixedText(String text, Icon icon) {
      this(text, icon, icon);
    }

    public FixedText(String text) {
      this(text, null, null);
    }

    public String getText() {
      return myText;
    }

    public static CanvasRenderable folder(String text, Icon openIcon, Icon closedIcon) {
      return new FixedText(text, openIcon, closedIcon);
    }
  }

}
