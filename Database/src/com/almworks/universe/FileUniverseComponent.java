package com.almworks.universe;

import com.almworks.api.container.RootContainer;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.util.properties.Role;

public class FileUniverseComponent implements ComponentDescriptor {
  public void registerActors(RootContainer container) {
    container.registerActorClass(Role.role("universe"), FileUniverse.class);
  }
}
