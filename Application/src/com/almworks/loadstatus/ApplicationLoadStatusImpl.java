package com.almworks.loadstatus;

import com.almworks.api.application.ApplicationLoadStatus;
import com.almworks.api.application.ExplorerComponent;
import com.almworks.api.engine.*;
import com.almworks.api.exec.ExceptionsManager;
import com.almworks.api.gui.MainMenu;
import com.almworks.api.gui.MainWindowManager;
import com.almworks.util.Env;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.*;
import com.almworks.util.ui.UIUtil;
import com.almworks.util.ui.actions.ActionRegistry;
import com.almworks.util.ui.actions.AnAction;
import org.almworks.util.Log;
import org.almworks.util.RuntimeInterruptedException;
import org.almworks.util.detach.DetachComposite;
import org.picocontainer.Startable;
import util.concurrent.SynchronizedInt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Collection;

class ApplicationLoadStatusImpl implements ApplicationLoadStatus, Startable {
  private static final String[] WATCHED_ACTIONS = Env.isMac()
    ? new String[] {
        MainMenu.Edit.EDIT_ITEM,
        MainMenu.File.SHOW_CONNECTION_INFO, MainMenu.File.REMOVE_CONNECTION, MainMenu.Edit.UPLOAD,
        MainMenu.Search.KEEP_LIVE_RESULTS, MainMenu.Search.NEW_DISTRIBUTION, MainMenu.Search.RUN_QUERY,
        MainMenu.Tools.SHOW_SYNCHRONIZATION_WINDOW, MainMenu.Tools.CONFIGURE_PROXY, }
    : new String[] {
        MainMenu.Edit.EDIT_ITEM, MainMenu.File.EXIT,
        MainMenu.File.SHOW_CONNECTION_INFO, MainMenu.File.REMOVE_CONNECTION, MainMenu.Edit.UPLOAD,
        MainMenu.Search.KEEP_LIVE_RESULTS, MainMenu.Search.NEW_DISTRIBUTION, MainMenu.Search.RUN_QUERY,
        MainMenu.Tools.SHOW_SYNCHRONIZATION_WINDOW, MainMenu.Tools.CONFIGURE_PROXY, MainMenu.Help.ABOUT, };

  private static final int TOTAL_WATCHED_COMPONENTS = 4;

  private final ActionRegistry myActionRegistry;
  private final Engine myEngine;
  private final ExplorerComponent myExplorerComponent;
  private final MainWindowManager myMainWindowManager;
  private final ExceptionsManager myExceptionsManager;

  private final BasicScalarModel<Boolean> myModel = BasicScalarModel.createWithValue(false, true);
  private final SynchronizedInt myComponentsReady = new SynchronizedInt(0);

  public ApplicationLoadStatusImpl(ActionRegistry actionRegistry, Engine engine, ExplorerComponent explorerComponent,
    MainWindowManager mainWindowManager, ExceptionsManager exceptionsManager)
  {
    myActionRegistry = actionRegistry;
    myEngine = engine;
    myExplorerComponent = explorerComponent;
    myMainWindowManager = mainWindowManager;
    myExceptionsManager = exceptionsManager;
  }

  public ScalarModel<Boolean> getApplicationLoadedModel() {
    return myModel;
  }

  public void start() {
    if (myExceptionsManager.isAnyExceptionOccured()) {
      emergencyStart();
    } else {
      final DetachComposite detach = new DetachComposite(true);
      detach.add(myExceptionsManager.addListener(new ExceptionsManager.Listener() {
        public void onException(ExceptionsManager.ExceptionEvent event) {
          detach.detach();
          emergencyStart();
        }
      }));
      detach.add(myModel.getEventSource().addStraightListener(new ScalarModel.Adapter<Boolean>() {
        public void onScalarChanged(ScalarModelEvent<Boolean> event) {
          Boolean started = event.getNewValue();
          if (started != null && started) {
            detach.detach();
          }
        }
      }));
    }
    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        try {
          watchMainWindowAppearance();
          watchEngineStart();
          watchActionsRegister();
          watchReadyTreeAndNameResolver();
        } catch (InterruptedException e) {
          emergencyStart();
          throw new RuntimeInterruptedException(e);
        }
      }
    });
    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        javax.swing.Timer timer = new Timer(60000, new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            boolean set = myModel.commitValue(false, true);
            if (set) {
              Log.warn("application status set to 'loaded' by timeout");
            }
          }
        });
        timer.setRepeats(false);
        timer.start();
      }
    });
  }

  public void stop() {
  }

  private boolean checkFrameOnScreen(JFrame frame) {
    if (!frame.isDisplayable())
      return false;
    Dimension size = frame.getSize();
    return size != null && (size.width != 0 || size.height != 0);
  }

  private void componentReady(String name) {
    log(name + " ready");
    if (myComponentsReady.increment() >= TOTAL_WATCHED_COMPONENTS) {
      myModel.commitValue(false, true);
    }
  }

  private void emergencyStart() {
    myModel.setValue(true);
  }

  private void watchActionsRegister() {
    final SynchronizedInt count = new SynchronizedInt(WATCHED_ACTIONS.length);
    if (count.get() == 0) {
      componentReady("actions");
      return;
    }

    final DetachComposite detach = new DetachComposite(true);
    for (String action : WATCHED_ACTIONS) {
      if (detach.isEnded())
        break;
      detach.add(myActionRegistry.addListener(action, new ActionRegistry.Listener() {
        public void onActionRegister(String actionId, AnAction action) {
          if (detach.isEnded())
            return;
          synchronized (count) {
            if (count.decrement() <= 0) {
              componentReady("actions");
              detach.detach();
            }
          }
        }
      }));
    }
  }

  private void watchBooleanModel(ScalarModel<Boolean> model, final String name) {
    final DetachComposite detach = new DetachComposite(true);
    model.getEventSource().addAWTListener(detach, new ScalarModel.Adapter<Boolean>() {
      public void onScalarChanged(ScalarModelEvent<Boolean> event) {
        Boolean value = event.getNewValue();
        if (value != null && value) {
          componentReady(name);
          detach.detach();
        }
      }
    });
  }

  private void watchEngineStart() throws InterruptedException {
    // todo extract common code
    Collection<Connection> connections = myEngine.getConnectionManager().getConnections().getFullCollectionBlocking();
    if (connections.size() == 0) {
      componentReady("engine");
      return;
    }
    final SynchronizedInt count = new SynchronizedInt(connections.size());
    final DetachComposite detach = new DetachComposite(true);
    for (Connection connection : connections) {
      detach.add(connection.getState().getEventSource().addStraightListener(new ScalarModel.Adapter<ConnectionState>() {
        public void onScalarChanged(ScalarModelEvent<ConnectionState> event) {
          if (detach.isEnded())
            return;
          ConnectionState state = event.getNewValue();
          if (state.isReady() || state.isDegrading()) {
            synchronized (count) {
              if (count.decrement() <= 0) {
                componentReady("engine");
                detach.detach();
              }
            }
          }
        }
      }));
    }
  }

  private void watchMainWindowAppearance() {
    final JFrame frame = myMainWindowManager.getMainFrame();
    ThreadGate.AWT.execute(new Runnable() {
      public void run() {
        if (checkFrameOnScreen(frame)) {
          componentReady("main window");
        } else {
          final DetachComposite detach = new DetachComposite(true);
          detach.add(UIUtil.addComponentListener(frame, new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
              check();
            }

            public void componentResized(ComponentEvent e) {
              check();
            }

            private void check() {
              if (checkFrameOnScreen(frame)) {
                componentReady("main window");
                detach.detach();
              }
            }
          }));
        }
      }
    });
  }

  private void watchReadyTreeAndNameResolver() {
    watchBooleanModel(myExplorerComponent.isNavigationTreeReady(), "navigation");
  }

  private void log(String message) {
    Log.debug("alsi: " + message);
  }
}
