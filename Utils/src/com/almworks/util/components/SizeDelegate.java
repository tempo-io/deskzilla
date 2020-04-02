package com.almworks.util.components;

import javax.swing.*;
import java.awt.*;

/**
 * todo make one-dimension size delegate and collection of three delegates to avoid code repetition
 *
 * @author sereda
 */
public abstract class SizeDelegate {
  public Dimension getMinimumSize(JComponent component, Dimension componentSize) {
    return componentSize;
  }

  public Dimension getMaximumSize(JComponent component, Dimension componentSize) {
    return componentSize;
  }

  public Dimension getPreferredSize(JComponent component, Dimension componentSize) {
    return componentSize;
  }

  public static Dimension maximum(JComponent component, SizeDelegate delegate, Dimension size) {
    if (delegate != null) {
      Dimension r = delegate.getMaximumSize(component, size);
      if (r != null)
        size = r;
    }
    return size;
  }

  public static Dimension minimum(JComponent component, SizeDelegate delegate, Dimension size) {
    if (delegate != null) {
      Dimension r = delegate.getMinimumSize(component, size);
      if (r != null)
        size = r;
    }
    return size;
  }

  public static Dimension preferred(JComponent component, SizeDelegate delegate, Dimension size) {
    if (delegate != null) {
      Dimension r = delegate.getPreferredSize(component, size);
      if (r != null)
        size = r;
    }
    return size;
  }

  public static abstract class SingleFunction extends SizeDelegate {
    public abstract Dimension getSize(JComponent component, Dimension componentSize);

    public Dimension getMaximumSize(JComponent t, Dimension componentSize) {
      Dimension size = getSize(t, componentSize);
      return size == null ? componentSize : size;
    }

    public Dimension getMinimumSize(JComponent t, Dimension componentSize) {
      Dimension size = getSize(t, componentSize);
      return size == null ? componentSize : size;
    }

    public Dimension getPreferredSize(JComponent t, Dimension componentSize) {
      Dimension size = getSize(t, componentSize);
      return size == null ? componentSize : size;
    }
  }
}
