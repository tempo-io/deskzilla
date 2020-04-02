package com.almworks.appinit;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.EmptyStackException;

/**
 * @author dyoma
 */
public class EventQueueReplacement extends EventQueue {
  //  @Nullable
  private static EventQueueReplacement INSTANCE;
  private final java.util.List<AWTEventPreprocessor> myPreprocessors = new ArrayList();
  private AWTEventPreprocessor[] myArray;

  private EventQueueReplacement() {
  }

  //  @ThreadSafe
  public void addPreprocessor(final AWTEventPreprocessor preprocessor) {
    synchronized (myPreprocessors) {
      myPreprocessors.add(preprocessor);
      resetArray();
    }
  }

  private void resetArray() {
    int size = myPreprocessors.size();
    myArray = size == 0 ? null : myPreprocessors.toArray(new AWTEventPreprocessor[size]);
  }

  //  @ThreadSafe
  public boolean removePreprocessor(AWTEventPreprocessor preprocessor) {
    synchronized (myPreprocessors) {
      boolean result = myPreprocessors.remove(preprocessor);
      resetArray();
      return result;
    }
  }

  public void pop() throws EmptyStackException {
    super.pop();
  }

  protected void dispatchEvent(AWTEvent event) {
    boolean consumed = false;
    AWTEventPreprocessor[] array = myArray;
    if (array != null) {
      for (AWTEventPreprocessor preprocessor : array) {
        consumed = preprocessor.preprocess(event, consumed);
        if (consumed) {
          break;
        }
      }
    }
    if (!consumed) {
      super.dispatchEvent(event);
    }
    consumed = consumed || ((event instanceof InputEvent) && ((InputEvent) event).isConsumed());
    if (array != null && !consumed) {
      for (AWTEventPreprocessor processor : array) {
        consumed = processor.postProcess(event, consumed);
        if (consumed) break;
      }
    }
  }

  //  @NotNull
  public static EventQueueReplacement ensureInstalled() {
    synchronized (EventQueueReplacement.class) {
      if (INSTANCE == null) {
        EventQueueReplacement queue = new EventQueueReplacement();
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(queue);
        INSTANCE = queue;
      }
      return INSTANCE;
    }
  }

  public static void detachPreprocessor(AWTEventPreprocessor preprocessor) {
    EventQueueReplacement instance;
    synchronized (EventQueueReplacement.class) {
      instance = INSTANCE;
      if (instance == null)
        return;
    }
    instance.removePreprocessor(preprocessor);
  }
}
