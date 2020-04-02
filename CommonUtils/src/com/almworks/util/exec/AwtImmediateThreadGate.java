package com.almworks.util.exec;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;

public class AwtImmediateThreadGate extends ImmediateThreadGate {
  protected void gate(Runnable runnable) throws InterruptedException, InvocationTargetException {
    if (Context.isAWT()) {
      runnable.run();
    } else {
      invoke(runnable);
    }
  }

  protected void invoke(Runnable runnable) throws InterruptedException, InvocationTargetException {
    EventQueue.invokeAndWait(runnable);
  }

  protected final Target getTarget() {
    return Target.AWT;
  }
}
