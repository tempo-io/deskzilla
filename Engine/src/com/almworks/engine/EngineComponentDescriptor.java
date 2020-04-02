package com.almworks.engine;

import com.almworks.api.container.RootContainer;
import com.almworks.api.engine.Engine;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.settings.engine.EngineActions;
import com.almworks.util.properties.Role;

/**
 * :todoc:
 *
 * @author sereda
 */
public class EngineComponentDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer registrator) {
    registrator.registerActorClass(Engine.ROLE, EngineImpl.class);
    registrator.registerActorClass(Role.role("engineActions"), EngineActions.class);
    registrator.registerActorClass(PeriodicalSynchronization.ROLE, PeriodicalSynchronization.class);
  }
}
