package com.almworks.gui;

import com.almworks.api.container.ComponentContainer;
import com.almworks.api.container.MutableComponentContainer;
import com.almworks.api.exec.ApplicationManager;
import com.almworks.api.gui.*;
import com.almworks.api.install.Setup;
import com.almworks.util.Env;
import com.almworks.util.commons.Factory;
import com.almworks.util.config.Configuration;
import com.almworks.util.config.ReadonlyConfiguration;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.*;
import com.almworks.util.ui.actions.*;
import com.almworks.util.ui.actions.globals.GlobalDataRoot;
import org.almworks.util.Log;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.File;
import java.util.Properties;

import static java.awt.Frame.ICONIFIED;

/**
 * @author : Dyoma
 * @deprecated
 */
public class MainWindowManagerImpl implements MainWindowManager {
  private final Configuration myLayout;
  private final JFrame myFrame;
  private final JPanel myMainPanel;
  @Nullable
  private JComponent myContentComponent;
  private final Lifecycle myLayoutDetach = new Lifecycle();
  private final ApplicationManager myApplicationManager;
  private final StatusBarImpl myStatusBarImpl;
  private final WindowControllerImpl myWindowController;
  private final WindowDescriptor myWindowDescriptor;
  private boolean myHideOnMinimizeAndClose;

  private final ComponentContainer myMainContainer;
  private Factory<JMenuBar> myMenuFactory;

  public MainWindowManagerImpl(ApplicationManager applicationManager, Configuration configuration,
    ComponentContainer container, WindowDescriptor windowDescriptor, ActionRegistry actionRegistry) {

    myLayout = configuration;
    myApplicationManager = applicationManager;
    myMainContainer = container;
    myWindowDescriptor = windowDescriptor;
    myStatusBarImpl = new StatusBarImpl();
    myMainPanel = new JPanel(new BorderLayout(0, 4));
    ThreadGate.AWT_IMMEDIATE.execute(new Runnable() {
      public void run() {
        GlobalDataRoot.install(myMainPanel);
      }
    });
    myFrame = new JFrame();
    myFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    if(!Env.isMac()) {
      myFrame.setIconImage(Icons.APPLICATION_LOGO_ICON_BIG.getImage());
    } else {
      if (Env.isMacLionOrNewer() && MacIntegration.isFullScreenSupported()) {
        MacIntegration.makeWindowFullScreenable(myFrame);
        actionRegistry.registerAction(MainMenu.Windows.TOGGLE_FULL_SCREEN, new AnAction() {
          public void update(UpdateContext context) throws CantPerformException {
            context.setEnabled(true);
            context.putPresentationProperty(PresentationKey.NAME, "Toggle Full Screen");
          }
          public void perform(ActionContext context) throws CantPerformException {
            MacIntegration.toggleFullScreen(myFrame);
          }
        });
      }
      MacIntegration.setReopenHandler(new Runnable() {
        @Override
        public void run() {
          if (UIUtil.isMinimized(myFrame)) {
            MainWindowManagerImpl.this.bringToFront();
          }
        }
      });
    }
    myFrame.setName(myFrame.getName() + "@MainFrame");
    ActionScope.set(myFrame, "MainFrame");
    myFrame.addWindowStateListener(new WindowStateListener() {
      public void windowStateChanged(WindowEvent e) {
        int os = e.getOldState();
        int ns = e.getNewState();
        if ((os & ICONIFIED) == 0 &&  (ns & ICONIFIED) != 0 && myHideOnMinimizeAndClose && !Env.isMac()) {
          if (myFrame.isVisible())
            myFrame.setVisible(false);
        }
      }
    });

    myWindowController = new WindowControllerImpl(myFrame, ApplicationManager.EXIT_ACTION);
    myFrame.setFocusTraversalPolicy(new FilteringFocusTraversalPolicy());
    myFrame.getContentPane().add(myMainPanel);
    myFrame.getContentPane().add(myStatusBarImpl.getComponent(), BorderLayout.SOUTH);
    MutableComponentContainer subcontainer = container.createSubcontainer("mainWindowContainer");
    subcontainer.registerActor(WindowController.ROLE, myWindowController);
    subcontainer.registerActorClass(DialogManager.ROLE, DialogManagerImpl2.class);
    myWindowController.addDataProvider(new ContainerDataProvider(subcontainer));
    JOptionPane.setRootFrame(myFrame);

    createMainFrame();
  }

  private void createMainFrame() {
    final Factory<JMenuBar> menuFactory = new Factory<JMenuBar>() {
      final Properties i18n = myWindowDescriptor.getMainMenuI18N();
      final MenuLoader menuLoader = new MenuLoader();
      final ReadonlyConfiguration menuConfig = myWindowDescriptor.getMainMenu();

      public JMenuBar create() {
        return menuLoader.loadMenuBar(menuConfig, i18n);
      }
    };

    if(Env.isMac()) {
      myMenuFactory = menuFactory;
    } else {
      myFrame.setJMenuBar(createMainFrameMenu(menuFactory));
      myMenuFactory = new Factory.Const(null);
    }

    StringBuilder title = new StringBuilder(myWindowDescriptor.getWindowTitle());

    if(Setup.isWorkspaceExplicit()) {
      File dir = Setup.getWorkspaceDir();
      assert dir != null;
      if (dir != null) {
        String workarea = dir.getAbsolutePath();
        if (workarea.endsWith(File.separator))
          workarea = workarea.substring(0, workarea.length() - File.separator.length());
        title.append(" - ").append(workarea);
      }
    }
    setTitle(title.toString());

    myApplicationManager.addListener(new ApplicationManager.Adapter() {
      public void onBeforeExit() {
        try {
          myFrame.dispose();
        } catch (Exception e) {
          // ignore
          Log.debug("on dispose: " + e);
        }
      }
    });
  }

  private JMenuBar createMainFrameMenu(Factory<JMenuBar> menuFactory) {
    final JMenuBar jmb = menuFactory.create();
    ComponentProperty.JUMP.putClientValue(jmb, myMainPanel);
    return jmb;
  }

  public void setContentComponent(@Nullable JComponent component) {
    if (myContentComponent != null)
      myMainPanel.remove(myContentComponent);
    if (component != null)
      myMainPanel.add(component, BorderLayout.CENTER);
    myContentComponent = component;
    myLayoutDetach.cycle();
    if (myContentComponent != null) {
      Dimension preferred = UIUtil.getDefaultScreenUserSize();
      preferred.width -= 100;
      preferred.height -= 120;
      Configuration config = myLayout.getOrCreateSubset("mainWindow");
      WindowUtil.setupWindow(myLayoutDetach.lifespan(), myFrame, config, true, preferred, false, null, null);
    }
    myMainPanel.invalidate();
    myMainPanel.revalidate();
    myMainPanel.repaint();
  }

  public void showWindow(boolean show) {
      if (!show) {
      myFrame.setVisible(false);
      return;
    }
    installDefaultMenuBar();
    Runnable showWindow = () -> {
      myFrame.setVisible(true);
//      if (menuBar != null) menuBar.addNotify();
    };
    SwingUtilities.invokeLater(showWindow);
  }

  private void installDefaultMenuBar() {
    if (!Env.isMac()) return;
    final JMenuBar menuBar = createMainFrameMenu(myMenuFactory);
    final MutableComponentContainer container = myMainContainer.createSubcontainer("macDefaultMenuBar");
    final DataProvider provider = new ContainerDataProvider(container);
    DataProvider.DATA_PROVIDER.putClientValue(menuBar, provider);
    MacIntegration.setDefaultMenuBar(menuBar);
    menuBar.addNotify(); // Must be done immediately. Otherwise menu items may be disabled on Mac
  }

  public void setTitle(String title) {
    myFrame.setTitle(title);
  }

  public StatusBar getStatusBar() {
    return myStatusBarImpl;
  }

  // kludge: dyoma review - see AboutDialog
  public JFrame getMainFrame() {
    return myFrame;
  }

  public void bringToFront() {
    restoreFrame();
    myFrame.toFront();
    grabFocus();
  }

  private void grabFocus() {
    // ??
    Component focusOwner = myFrame.getFocusOwner();
    if (focusOwner != null)
      focusOwner.requestFocus();
  }

  public void minimize() {
    UIUtil.minimizeFrame(myFrame);
  }

  public void setHideOnMinimizeAndClose(boolean hide) {
    myHideOnMinimizeAndClose = hide;
    myWindowController.setHideOnClose(hide);
  }

  private void restoreFrame() {
    UIUtil.restoreFrame(myFrame);
  }
}
