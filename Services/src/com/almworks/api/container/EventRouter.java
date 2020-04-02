package com.almworks.api.container;

import com.almworks.util.exec.ThreadGate;
import com.almworks.util.properties.Role;
import org.almworks.util.detach.Detach;

public interface EventRouter {
  Role<EventRouter> ROLE = Role.role("eventRouter");

  /**
   * Same as calling {@link #addListener(ThreadGate, Object, Class)} for each interface that listener implements.
   */
  Detach addListener(ThreadGate gate, Object listener);

  <I, C extends I> Detach addListener(ThreadGate gate, C listener, Class<I> listenerClass);

  void removeListener(Object listener);

  <I> I getEventSink(Class<I> listenerClass, boolean globalSink);
}
