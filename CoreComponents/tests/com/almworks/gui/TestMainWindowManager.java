package com.almworks.gui;

import com.almworks.api.gui.*;
import com.almworks.util.commons.Factory;
import com.almworks.util.ui.UIComponentWrapper;
import org.almworks.util.detach.Detach;

import javax.swing.*;

public class TestMainWindowManager implements MainWindowManager {
  private StatusBar myStatusBar;
  private Factory<JMenuBar> myMenuFactory;

  public TestMainWindowManager() {
    myStatusBar = new TestBar();
    myMenuFactory = new Factory.Const(null);
  }

  public void setContentComponent(JComponent component) {
  }

  public void showWindow(boolean show) {
  }

  public StatusBar getStatusBar() {
    return myStatusBar;
  }

  public JFrame getMainFrame() {
    return null;
  }

//  public Factory<JMenuBar> getMenuFactory() {
//    return myMenuFactory;
//  }

  public void bringToFront() {

  }

  public void minimize() {
    //todo imlementation
  }

  public void setHideOnMinimizeAndClose(boolean hide) {

  }

  private class TestBar implements StatusBar {
    public Detach addComponent(Section section, StatusBarComponent component) {
      return Detach.NOTHING;
    }

    public Detach addComponent(Section section, StatusBarComponent component, double weight) {
      return Detach.NOTHING;
    }

    public Detach showContextComponent(UIComponentWrapper component) {
      return Detach.NOTHING;
    }
  }
}
