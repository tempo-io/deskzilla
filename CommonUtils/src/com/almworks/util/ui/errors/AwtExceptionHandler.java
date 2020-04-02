package com.almworks.util.ui.errors;

import com.almworks.util.threads.MainThreadGroup;

/**
 * @see java.awt.EventDispatchThread#handleException(Throwable)
 */
public class AwtExceptionHandler {
  public static void staticInitialize() {
    System.setProperty("sun.awt.error.handler", AwtExceptionHandler.class.getName());
    System.setProperty("sun.awt.exception.handler", AwtExceptionHandler.class.getName());
  }

  public void handle(Throwable throwable) {
    try {
      if (throwable != null) {
        MainThreadGroup.getOrCreate().uncaughtException(Thread.currentThread(), throwable);
      }
    } catch (Throwable e) {
      if (throwable != null) {
        throwable.printStackTrace();
      }
      e.printStackTrace();
    }
  }
}
