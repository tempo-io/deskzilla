package com.almworks.container;

import com.almworks.api.container.EventRouter;
import com.almworks.util.ReflectionUtil;
import com.almworks.util.events.FireEventSupport;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import java.util.Iterator;
import java.util.Map;

class EventRouterImpl implements EventRouter {
  private final Map<Class, FireEventSupport> myMap = Collections15.hashMap();
  private final @Nullable EventRouterImpl myParent;

  public EventRouterImpl(@Nullable EventRouterImpl parent) {
    myParent = parent;
  }

  public Detach addListener(ThreadGate gate, Object listener) {
    assert listener != null;
    Class[] interfaces = ReflectionUtil.getAllInterfaces(listener.getClass());
    if (interfaces == null || interfaces.length == 0)
      throw new IllegalArgumentException(listener + " does not implement any interface");
    DetachComposite detach = new DetachComposite();
    for (int i = 0; i < interfaces.length; i++) {
      detach.add(addListener(gate, listener, interfaces[i]));
    }
    return detach;
  }

  public <I, C extends I> Detach addListener(ThreadGate gate, C listener, Class<I> listenerClass) {
    assert listenerClass != null;
    assert listener != null;
    if (!listenerClass.isInstance(listener))
      throw new IllegalArgumentException(listener + " is not an instance of " + listenerClass);
    FireEventSupport<I> support = getSupport(listenerClass);
    return support.addListener(gate, listener);
  }

  <I> FireEventSupport<I> getSupport(Class<I> listenerClass) {
    synchronized (myMap) {
      FireEventSupport<I> support = myMap.get(listenerClass);
      if (support == null) {
        support = FireEventSupport.createSynchronized(listenerClass);
        if (myParent != null) {
          // route events from parent
          FireEventSupport<I> parentSupport = myParent.getSupport(listenerClass);
          parentSupport.addChainedSource(support);
        }
        myMap.put(listenerClass, support);
      }
      return support;
    }
  }

  public void removeListener(Object listener) {
    synchronized (myMap) {
      for (Iterator<FireEventSupport> ii = myMap.values().iterator(); ii.hasNext();) {
        ii.next().removeListener(listener);
      }
    }
  }

  /**
   * Gets event dispatcher for the given listener interfact. If globalSink is true, event sink
   * is taken from the topmost container, thus events will spread to everybody who listens. If
   * globalSink is false, events will spread only to those listeners that are registered in this
   * or children containers.
   */
  public <I> I getEventSink(Class<I> listenerClass, boolean globalSink) {
    if (globalSink && myParent != null)
      return myParent.getEventSink(listenerClass, globalSink);
    else
      return getSupport(listenerClass).getDispatcher();
  }

  public <I> I getEventSink(Class<I> listenerClass) {
    return getEventSink(listenerClass, false);
  }
}
