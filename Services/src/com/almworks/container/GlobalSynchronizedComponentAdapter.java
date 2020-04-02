package com.almworks.container;

import org.picocontainer.*;
import org.picocontainer.defaults.DecoratingComponentAdapter;

public class GlobalSynchronizedComponentAdapter extends DecoratingComponentAdapter {
  public GlobalSynchronizedComponentAdapter(ComponentAdapter delegate) {
    super(delegate);
  }

  public Object getComponentInstance(PicoContainer container)
    throws PicoInitializationException, PicoIntrospectionException
  {
    synchronized (GlobalSynchronizedComponentAdapter.class) {
      return super.getComponentInstance(container);
    }
  }
}
