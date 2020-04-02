package com.almworks.util.components;

import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

/**
 * @author dyoma
 */
public abstract class SizeCalculator1D {
  public abstract int getPrefLength(@NotNull JComponent context);

  public static SizeCalculator1D fixedPixels(final int pixels) {
    return new SizeCalculator1D() {
      public int getPrefLength(@NotNull JComponent context) {
        return pixels;
      }
    };
  }

  public static SizeCalculator1D text(final String text) {
    return new SizeCalculator1D() {
      public int getPrefLength(@NotNull JComponent context) {
        return getFontMetrics(context).stringWidth(text);
      }
    };
  }

  public static SizeCalculator1D letterMWidth(final int count) {
    return new SizeCalculator1D() {
      public int getPrefLength(@NotNull JComponent context) {
        return getFontMetrics(context).charWidth('M') * count;
      }
    };
  }

  public static SizeCalculator1D sum(final int gap, final SizeCalculator1D ... calculators) {
    return new SizeCalculator1D() {
      public int getPrefLength(@NotNull JComponent context) {
        int sum = 0;
        for (SizeCalculator1D calculator : calculators) {
          int length = calculator.getPrefLength(context);
          if (length > 0 && sum > 0)
            sum += gap;
          sum += length;
        }
        return sum;
      }
    };
  }

  public static SizeCalculator1D textLines(final int number) {
    return new SizeCalculator1D() {
      @Override
      public int getPrefLength(@NotNull JComponent context) {
        FontMetrics metrics = context.getFontMetrics(context.getFont());
        return metrics.getHeight() * number;
      }
    };
  }

  protected FontMetrics getFontMetrics(@NotNull JComponent context) {
    return context.getFontMetrics(context.getFont());
  }
}
