package com.almworks.gui;

import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.gui.FrameBuilder;
import com.almworks.util.Env;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.DataProvider;
import com.almworks.util.ui.actions.MenuLoader;
import com.almworks.util.ui.actions.globals.GlobalDataRoot;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;

import javax.swing.*;
import java.awt.*;
import java.util.Properties;

/**
 * @author : Dyoma
 */
class FrameBuilderImpl extends BasicWindowBuilderImpl<FrameBuilderImpl.MyFrame> implements FrameBuilder {
  private Dimension myPreferedSize;
  private MenuLoader myMenuLoader;
  private JMenuBar myMenuBar;
  private boolean myAddRootPaneDataRoot = false;
  private boolean myResizable = true;

  public FrameBuilderImpl(MutableComponentContainer container, Configuration configuration) {
    super(container, configuration);
  }

  protected MyFrame createWindow(final Detach disposeNotification) {
    final MyFrame frame = new MyFrame();
    frame.setFocusTraversalPolicy(new FilteringFocusTraversalPolicy());    
    frame.addToDispose(disposeNotification);
    frame.setTitle(getTitle());
    frame.setResizable(myResizable);

//    if (myMenuBar == null) {
//      MainWindowManager mwm = Context.require(MainWindowManager.class);
//      myMenuBar = mwm.getMenuFactory().create();
//    }

    if (myMenuBar != null) {
      frame.setJMenuBar(myMenuBar);
      ComponentProperty.JUMP.putClientValue(myMenuBar, frame.getRootPane());
    }

    if (myAddRootPaneDataRoot) {
      GlobalDataRoot.install(frame.getRootPane());
    }

    return frame;
  }

  public void setPreferredSize(Dimension size) {
    myPreferedSize = size;
  }

  @Override
  public boolean isModal() {
    return false;
  }

  protected Dimension getPreferredSize() {
    return myPreferedSize;
  }

  public void insertContent(MyFrame window, UIComponentWrapper content) {
    window.getContentPane().add(content.getComponent(), BorderLayout.CENTER);
    window.addToDispose(new UIComponentWrapper.Disposer(content));
  }

  private MenuLoader getMenuLoader() {
    if (myMenuLoader == null) {
      myMenuLoader = new MenuLoader();
    }
    return myMenuLoader;
  }

  public void setMenu(ReadonlyConfiguration menuContent, Properties i18n) {
    myMenuBar = getMenuLoader().loadMenuBar(menuContent, i18n);
  }

  public void setRootPaneGlobalRoot(boolean setRoot) {
    myAddRootPaneDataRoot = setRoot;
  }

  @Override
  public void setResizable(boolean resizable) {
    myResizable = resizable;
  }

  static class MyFrame extends JFrame {
    private final DetachComposite myDetach = new DetachComposite();
    private boolean myDisposing = false;

    public MyFrame() throws HeadlessException {
      super(getDisplay());
      if(!Env.isMac()) {
        setIconImage(Icons.APPLICATION_LOGO_ICON_BIG.getImage());
      }
    }

    private static GraphicsConfiguration getDisplay() {
      Window window = UIUtil.getDefaultDialogOwner();
      if (window != null)
        return window.getGraphicsConfiguration();
      else
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    }

    public void addToDispose(Detach detach) {
      myDetach.add(detach);
    }

    public void dispose() {
      myDisposing = true;
      super.dispose();
      myDetach.detach();
      getContentPane().removeAll();
      DataProvider.DATA_PROVIDER.removeAllProviders(getRootPane());
    }

    public boolean isGoingToDispose() {
      return myDisposing;
    }
  }
}