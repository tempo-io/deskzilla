package com.almworks.api.application.util;

import com.almworks.api.application.ModelKey;
import com.almworks.util.components.CollectionRenderer;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.properties.PropertyMap;
import com.almworks.util.ui.ColorUtil;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.swing.AwtUtil;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;

public class DummyShadingRenderer implements CollectionRenderer<PropertyMap> {
  @NotNull
  private final CollectionRenderer <PropertyMap> myBaseRenderer;
  @Nullable
  private final ModelKey<Boolean> myDummyFlag;
  private final JComponent myNoDummyValue = new ShadingComponent();

  public DummyShadingRenderer(
    @NotNull CollectionRenderer<PropertyMap> baseRenderer,
    @Nullable ModelKey<Boolean> dummyFlag)
  {
    myBaseRenderer = baseRenderer;
    myDummyFlag = dummyFlag;
  }

  public JComponent getRendererComponent(CellState state, PropertyMap values) {
    if (myDummyFlag == null || myDummyFlag.getValue(values) != Boolean.TRUE) {
      return myBaseRenderer.getRendererComponent(state, values);
    } else {
      myNoDummyValue.setForeground(ColorUtil.between(state.getForeground(), state.getOpaqueBackground(), 0.75f));
      state.setBackgroundTo(myNoDummyValue, true);
      myNoDummyValue.setBorder(state.getBorder());
      return myNoDummyValue;
    }
  }

  private static class ShadingComponent extends JComponent {
    private Paint myPaint = null;
    private Color myCachedForeground = null;

    public ShadingComponent() {
      setOpaque(true);
    }

    protected void paintComponent(Graphics g) {
      Graphics2D graphics = (Graphics2D) g.create();
      try {
        AwtUtil.applyRenderingHints(graphics);
        graphics.setPaint(getBackground());
        graphics.fillRect(0, 0, getWidth(), getHeight());
        graphics.setPaint(getPaint());
        graphics.fillRect(0, 0, getWidth(), getHeight());
      } finally {
        graphics.dispose();
      }
    }

    private Paint getPaint() {
      Color foreground = getForeground();
      if (myPaint != null && Util.equals(myCachedForeground, foreground))
        return myPaint;
      myCachedForeground = foreground;
      myPaint = UIUtil.createShadingPaint(null, foreground, 6);
      return myPaint;
    }
  }
}
