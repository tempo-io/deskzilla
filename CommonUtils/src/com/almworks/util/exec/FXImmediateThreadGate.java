package com.almworks.util.exec;

import com.sun.javafx.application.PlatformImpl;

import java.lang.reflect.InvocationTargetException;

public class FXImmediateThreadGate extends ImmediateThreadGate {
  @Override
  protected void gate(Runnable runnable) throws InterruptedException, InvocationTargetException {
    PlatformImpl.runAndWait(runnable);
  }
}
