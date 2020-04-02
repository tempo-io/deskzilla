package com.almworks.util.ui.widgets.util;

import com.almworks.util.ui.swing.AwtUtil;
import com.almworks.util.ui.widgets.*;
import com.almworks.util.ui.widgets.genutil.Log;
import org.almworks.util.TypedKey;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.List;

public class WidgetUtil {
  private static final Log<WidgetUtil> log = Log.get(WidgetUtil.class);
  private static final String DOTS = "\u2026";

  public static boolean isMouseOver(EventContext context) {
    MouseEventData data = context.getData(MouseEventData.KEY);
    if (data == null)
      return false;
    if (data.getEventId() == MouseEvent.MOUSE_EXITED)
      return false;
    int x = data.getX();
    if (x < 0 || x >= context.getWidth())
      return false;
    int y = data.getY();
    return y >= 0 && y < context.getHeight();
  }

  public static void detachWidget(Widget<?> widget, WidgetHost host) {
    WidgetAttach attach = widget.getAttach();
    if (attach != null) attach.detach(host);
  }

  public static void attachWidget(Widget<?> widget, WidgetHost host) {
    WidgetAttach attach = widget.getAttach();
    if (attach != null) attach.attach(host);
  }

  public static void activateWidget(Widget<?> widget, HostCell cell) {
    CellActivate activate = widget.getActivate();
    if (activate != null) activate.activate(cell);
  }

  public static void deactivate(Widget<?> widget, HostCell cell) {
    CellActivate activate = widget.getActivate();
    if (activate == null) return;
    JComponent component = cell.getLiveComponent();
    activate.deactivate(cell, component);
  }

  public static void reshapeLifeTextField(JTextField field, CellContext context) {
    Insets insets = field.getInsets();
    Insets margin = field.getMargin();
    int x = context.getHostX() - insets.left - margin.left;
    int y = context.getHostY() - insets.top - margin.top;
    int width = context.getWidth() + AwtUtil.getInsetWidth(insets) + AwtUtil.getInsetWidth(margin);
    int height = context.getHeight() + AwtUtil.getInsetHeight(insets) + AwtUtil.getInsetHeight(margin);
    height = Math.min(height, field.getPreferredSize().height);
    AwtUtil.setBounds(field, x, y, width, height);
  }

  @Nullable
  public static HostCell getCommonAncestor(@Nullable HostCell cell1, @Nullable HostCell cell2) {
    if (cell1 == cell2) return cell1;
    if (cell1 == null || cell2 == null) return null;
    int depth1 = cell1.getTreeDepth();
    int depth2 = cell2.getTreeDepth();
    while (depth1 > depth2 && cell1 != null) {
      cell1 = cell1.getParent();
      depth1--;
    }
    while (depth2 > depth1 && cell2 != null) {
      cell2 = cell2.getParent();
      depth2--;
    }
    while (cell1 != null && cell2 != null && cell1 != cell2) {
      cell1 = cell1.getParent();
      cell2 = cell2.getParent();
    }
    return cell1 == cell2 ? cell1 : null; 
  }

  public static void reshapeFullCellComponent(EventContext context) {
    HostCell cell = context.getActiveCell();
    if (cell == null) return;
    JComponent component = cell.getLiveComponent();
    if (component == null) return;
    AwtUtil.setBounds(component, context.getHostBounds(null));
  }

  public static <T> void postEventToAllDescendants(HostCell ancestor, TypedKey<T> reason, T data) {
    for (HostCell child : ancestor.getChildrenList()) {
      child.postEvent(reason, data);
      postEventToAllDescendants(child, reason, data);
    }
  }

  @Nullable
  public static HostCell findDescendant(HostCell ancestor, Widget<?> widget) {
    List<HostCell> children = ancestor.getChildrenList();
    for (HostCell cell : children) if (cell.getWidget() == widget) return cell;
    for (HostCell cell : children) {
      HostCell result = findDescendant(cell, widget);
      if (result != null) return result;
    }
    return null;
  }

  public static int centerYText(GraphContext context) {
    FontMetrics metrics = context.getFontMetrics();
    int offset = (context.getHeight() - metrics.getHeight()) / 2;
    int y = offset + metrics.getAscent();
    return y;
  }
}
