package com.almworks.util.ui.macosx;

import com.almworks.util.Env;
import com.almworks.util.ui.actions.CantPerformException;
import org.almworks.util.Util;
import org.almworks.util.detach.Detach;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.*;
import java.util.*;

public class FullScreenFeature extends AppIntegrationFeature {
  private static final String FULL_SCREEN_UTILITIES = "com.apple.eawt.FullScreenUtilities";

  private static final String SET_WINDOW_CAN_FULL_SCREEN = "setWindowCanFullScreen";
  private static final Class<?>[] WINDOW_BOOLEAN = { Window.class, boolean.class };

  private static final String ADD_FULL_SCREEN_LISTENER_TO = "addFullScreenListenerTo";
  private static final String REMOVE_FULL_SCREEN_LISTENER_FROM = "removeFullScreenListenerFrom";
  
  private static final String REQUEST_TOGGLE_FULL_SCREEN = "requestToggleFullScreen";
  private static final Class<?>[] WINDOW = { Window.class };
  
  private static final String FULL_SCREEN_LISTENER = "com.apple.eawt.FullScreenListener";
  private static final String WINDOW_ENTERED_FULL_SCREEN = "windowEnteredFullScreen";
  private static final String WINDOW_ENTERING_FULL_SCREEN = "windowEnteringFullScreen";
  private static final String WINDOW_EXITED_FULL_SCREEN = "windowExitedFullScreen";
  private static final String WINDOW_EXITING_FULL_SCREEN = "windowExitingFullScreen";
  
  private static final Map<String, FullScreenEvent.Type> EVENT_TYPES;
  static {
    Map<String, FullScreenEvent.Type> map = new HashMap<String, FullScreenEvent.Type>();
    map.put(WINDOW_ENTERED_FULL_SCREEN, FullScreenEvent.Type.ENTERED);
    map.put(WINDOW_ENTERING_FULL_SCREEN, FullScreenEvent.Type.ENTERING);
    map.put(WINDOW_EXITED_FULL_SCREEN, FullScreenEvent.Type.EXITED);
    map.put(WINDOW_EXITING_FULL_SCREEN, FullScreenEvent.Type.EXITING);
    EVENT_TYPES = Collections.unmodifiableMap(map);
  }

  static FullScreenFeature create() {
    if (Env.isMacLionOrNewer()
      && methodExists(FULL_SCREEN_UTILITIES, SET_WINDOW_CAN_FULL_SCREEN, WINDOW_BOOLEAN)
      && methodExists(EAWT_APPLICATION, REQUEST_TOGGLE_FULL_SCREEN, WINDOW)
      && classExists(FULL_SCREEN_LISTENER))
    {
      return new FullScreenFeature();
    }
    return null;
  }

  private FullScreenFeature() {}

  public void setWindowFullScreenable(Window window, boolean value) {
    try {
      reflectivelyCall(FULL_SCREEN_UTILITIES, null, SET_WINDOW_CAN_FULL_SCREEN, WINDOW_BOOLEAN, window, value);
    } catch (CantPerformException e) {
      warn(e);
    }
  }

  public boolean isWindowFullScreenable(Window window) {
    if (window instanceof RootPaneContainer) {
      return Boolean.TRUE.equals(((RootPaneContainer)window).getRootPane().getClientProperty("apple.awt.fullscreenable"));
    }
    return false;
  }

  public void toggleFullScreen(Window window) {
    try {
      reflectivelyCall(null, getEawtApplication(), REQUEST_TOGGLE_FULL_SCREEN, WINDOW, window);
    } catch (CantPerformException e) {
      warn(e);
    }
  }
  
  public Detach addFullScreenListener(final Window target, final FullScreenEvent.Listener listener) {
    try {
      final Class<?>[] listenerIntf = { getClass(FULL_SCREEN_LISTENER) };
      final Class<?>[] addRemoveArgs = { Window.class, listenerIntf[0] };

      final Object proxy = Proxy.newProxyInstance(listenerIntf[0].getClassLoader(), listenerIntf, new FullScreenHandler(target, listener));
      reflectivelyCall(FULL_SCREEN_UTILITIES, null, ADD_FULL_SCREEN_LISTENER_TO, addRemoveArgs, target, proxy);

      return new Detach() {
        @Override
        protected void doDetach() throws Exception {
          try {
            reflectivelyCall(FULL_SCREEN_UTILITIES, null, REMOVE_FULL_SCREEN_LISTENER_FROM, addRemoveArgs, target, proxy);
          } catch (CantPerformException e) {
            warn(e);
          }
        }
      };
    } catch (CantPerformException e) {
      warn(e);
    }
    return Detach.NOTHING;
  }

  private static class FullScreenHandler implements InvocationHandler {
    private final Window myTarget;
    private final FullScreenEvent.Listener myListener;

    public FullScreenHandler(Window target, FullScreenEvent.Listener listener) {
      myTarget = target;
      myListener = listener;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      FullScreenEvent.Type type = EVENT_TYPES.get(method.getName());
      if (type != null && args.length == 1) {
        try {
          Window window = Util.castNullable(Window.class, reflectivelyCall(null, args[0], "getWindow", NO_ARGS));
          if (window == myTarget) {
            myListener.onFullScreenEvent(new FullScreenEvent(window, type));
          }
        } catch (CantPerformException e) {
          warn(e);
        }
      }
      return null;
    }
  }
}
