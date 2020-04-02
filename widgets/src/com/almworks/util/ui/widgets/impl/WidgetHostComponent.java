package com.almworks.util.ui.widgets.impl;

import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WidgetHostComponent extends JComponent implements IWidgetHostComponent {
  private final TraversalPolicy myFocusPolicy;
  private final MyListener myListener;
  private final AtomicBoolean myListenWheel = new AtomicBoolean(true);
  private static final ComponentUI UI = new HostUI();
  private HostComponentState<?> myState = null;
  private Color mySelectionColor = null;

  public WidgetHostComponent() {
    setUI(UI);
    myListener = new MyListener();
    addMouseListener(myListener);
    addMouseMotionListener(myListener);
    addMouseWheelListener(myListener);
    addKeyListener(myListener);
    setFocusable(true);
    setFocusTraversalKeysEnabled(false);
    setFocusTraversalPolicyProvider(true);
    //noinspection ThisEscapedInObjectConstruction
    myFocusPolicy = new TraversalPolicy(this);
    setFocusTraversalPolicy(myFocusPolicy);
    setToolTipText(""); // Enable tooltip support
  }

  public void setListenMouseWheel(boolean listen) {
    if (listen == myListenWheel.get()) return;
    while (listen != myListenWheel.get()) {
      if (myListenWheel.compareAndSet(!listen, listen)) {
        if (listen) addMouseWheelListener(myListener);
        else removeMouseWheelListener(myListener);
      }
    }
  }

  public <T> HostComponentState<T> createState() {
    return new HostComponentState<T>(this);
  }

  public void setState(@Nullable HostComponentState<?> state) {
    if (state == myState)
      return;
    if (myState != null)
      myState.deactivate();
    myState = state;
    if (myState != null && isDisplayable()) {
      myState.activate();
    }
    updateAll();
  }

  public void setSelectionColor(Color selectionColor) {
    if (selectionColor == null)
      selectionColor = getBackground();
    if (selectionColor.equals(mySelectionColor))
      return;
    mySelectionColor = selectionColor;
    repaint();
  }

  @Override
  public void reshape(int x, int y, int w, int h) {
    int oldWidth = getWidth();
    int oldHeight = getHeight();
    super.reshape(x, y, w, h);
    if (myState == null)
      return;
    if (oldWidth != w || oldHeight != h)
      myState.hostReshaped(oldWidth, oldHeight);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (myState != null) {
      myState.activate();
    }
  }

  @Override
  public void removeNotify() {
    if (myState != null)
      myState.deactivate();
    super.removeNotify();
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    return myState.getToolTipText(event);
  }

  @Override
  public void setCursor(Cursor cursor) {
    if (cursor == null)
      cursor = Cursor.getDefaultCursor();
    Cursor oldCursor = getCursor();
    if (oldCursor != cursor)
      super.setCursor(cursor);
  }

  public void updateAll() {
    invalidate();
    revalidate();
    repaint();
  }

  public JComponent getHostComponent() {
    return this;
  }

  public void fullRefresh() {
    invalidate();
    revalidate();
    repaintAll();
  }

  public void repaintAll() {
    repaint();
  }

  @Override
  public void updateUI() {
    if (myState != null)
      myState.updateUI();
  }

  public Color getSelectionBg() {
    return mySelectionColor;
  }

  public void setRemovingComponent(Component component) {
    myFocusPolicy.setCurrentRemove(component);
  }

  @Override
  public void widgetRequestsFocus() {
    requestFocusInWindow();
  }

  private void dispatchMouse(MouseEvent e) {
    if (myState != null)
      try {
        myState.dispatchMouse(e);
      } catch (Throwable e1) {
        Log.error(e1);
        e1.printStackTrace();
      }
  }

  private void dispatchKeyEvent(KeyEvent e) {
    if (myState != null)
      myState.dispatchKeyEvent(e);
  }

  public HostComponentState<?> getState() {
    return myState;
  }

  private static class HostUI extends ComponentUI {
    @Override
    public void installUI(JComponent c) {
      WidgetHostComponent host = (WidgetHostComponent) c;
      host.setOpaque(true);
      LookAndFeel.installColorsAndFont(c, "List.background", "List.foreground", "Label.font");
      host.setSelectionColor(UIManager.getColor("List.selectionBackground"));
    }

    @Override
    public void paint(Graphics g, JComponent c) {
      HostComponentState<?> state = ((WidgetHostComponent) c).myState;
      if (state != null)
        state.paint((Graphics2D) g);
    }

    @Nullable
    @Override
    public Dimension getPreferredSize(JComponent c) {
      HostComponentState<?> state = ((WidgetHostComponent) c).myState;
      if (state == null)
        return null;
      int width = state.getPreferedWidth();
      if (width < 0)
        return null;
      int height = state.getPreferedHeight(width);
      if (height < 0)
        return null;
      return new Dimension(width, height);
    }

    @Nullable
    @Override
    public Dimension getMaximumSize(JComponent c) {
      return null;
    }

    @Nullable
    @Override
    public Dimension getMinimumSize(JComponent c) {
      return null;
    }

    @Override
    public void update(Graphics g, JComponent c) {
      g.setColor(c.getForeground());
      g.setFont(c.getFont());
      super.update(g, c);
    }
  }


  private class MyListener implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
    public void keyTyped(KeyEvent e) {
      dispatchKeyEvent(e);
    }

    public void keyPressed(KeyEvent e) {
      dispatchKeyEvent(e);
    }

    public void keyReleased(KeyEvent e) {
      dispatchKeyEvent(e);
    }

    public void mouseClicked(MouseEvent e) {
      dispatchMouse(e);
    }

    public void mousePressed(MouseEvent e) {
      dispatchMouse(e);
    }

    public void mouseReleased(MouseEvent e) {
      dispatchMouse(e);
    }

    public void mouseEntered(MouseEvent e) {
      dispatchMouse(e);
    }

    public void mouseExited(MouseEvent e) {
      dispatchMouse(e);
    }

    public void mouseDragged(MouseEvent e) {
      dispatchMouse(e);
    }

    public void mouseMoved(MouseEvent e) {
      dispatchMouse(e);
    }

    public void mouseWheelMoved(MouseWheelEvent e) {
      dispatchMouse(e);
    }
  }
}
