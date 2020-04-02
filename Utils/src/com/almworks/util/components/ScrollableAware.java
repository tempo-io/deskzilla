package com.almworks.util.components;

import com.almworks.util.ui.ComponentProperty;

import java.awt.*;

public interface ScrollableAware {
  ComponentProperty<ScrollableAware> COMPONENT_PROPERTY = ComponentProperty.createProperty("ScrollableAware");

  boolean wantFillViewportHeight(Dimension viewport, Dimension preferred);

  boolean wantFillViewportWidth(Dimension viewport, Dimension preferred);
}
