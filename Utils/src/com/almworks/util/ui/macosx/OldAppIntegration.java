package com.almworks.util.ui.macosx;

import com.almworks.util.Env;
import com.almworks.util.ui.MacIntegration;
import com.almworks.util.ui.actions.CantPerformException;
import org.almworks.util.Log;

import javax.swing.*;
import java.lang.reflect.*;
import java.util.*;

/**
 * The "old-style" integration: pre-Leopard-Java-Update-8, pre-Snow-Leopard-Java-Update-3.
 * Cannot open URIs.
 */
public class OldAppIntegration extends NullAppIntegration {
  /** Whether the ApplicationHandler had already been installed or not. */
  private volatile boolean myHandlerInstalled = false;

  /** A map from method names in com.apple.eawt.ApplicationListener to Runnables that get called. */
  private final Map<String, Runnable> myHandlerMap = Collections.synchronizedMap(new HashMap<String, Runnable>());

  @Override
  public boolean isAvailable() {
    try {
      getEawtApplication();
      return true;
    } catch(CantPerformException e) {
      Log.debug(e);
      return false;
    }
  }

  @Override
  public void installMacHandlers() {
    if(myHandlerInstalled) {
      return;
    }

    try {
      final Class<?>[] appListenerIntf = { getClass("com.apple.eawt.ApplicationListener") };

      final Object listenerProxy = Proxy.newProxyInstance(appListenerIntf[0].getClassLoader(), appListenerIntf,
        new InvocationHandler() {
          public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            final Runnable handler = myHandlerMap.get(method.getName());

            boolean handled = false;
            if(handler != null) {
              try {
                handler.run();
                handled = true;
              } catch(MacIntegration.EventCancelled e) {
                handled = false;
              }
            }

            try {
              reflectivelyCall(null, args[0], "setHandled", new Class<?>[] {Boolean.TYPE}, handled);
            } catch (CantPerformException e) {
              if (Env.isMac()) {
                Log.debug(e.getCause());
              }
            }
            return null;
          }
        });

      reflectivelyCall(null, getEawtApplication(), "addApplicationListener", appListenerIntf, listenerProxy);
    } catch(CantPerformException e) {
      if (Env.isMac()) {
        Log.warn(e.getCause());
      }
    } finally {
      myHandlerInstalled = true;
    }
  }

  @Override
  public void setQuitHandler(Runnable quitHandler) {
    myHandlerMap.put("handleQuit", quitHandler);
  }

  @Override
  public void setAboutHandler(Runnable aboutHandler) {
    myHandlerMap.put("handleAbout", aboutHandler);
  }

  @Override
  public void setReopenHandler(Runnable reopenHandler) {
    myHandlerMap.put("handleReOpenApplication", reopenHandler);
  }

  @Override
  public void setDefaultMenuBar(JMenuBar menuBar) {
    try {
      reflectivelyCall(null, getEawtApplication(), "setDefaultMenuBar", new Class<?>[] { JMenuBar.class }, menuBar);
    } catch (CantPerformException e) {
      if (Env.isMac()) {
        Log.warn(e.getCause());
      }
    }
  }
}
