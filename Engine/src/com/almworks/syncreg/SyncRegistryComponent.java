package com.almworks.syncreg;

import com.almworks.api.container.RootContainer;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.api.syncreg.SyncRegistry;

public class SyncRegistryComponent implements ComponentDescriptor {
  public void registerActors(RootContainer container) {
    container.registerActorClass(SyncRegistry.ROLE, SyncRegistryImpl.class);
  }
}
