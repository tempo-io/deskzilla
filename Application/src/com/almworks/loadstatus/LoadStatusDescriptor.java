package com.almworks.loadstatus;

import com.almworks.api.application.ApplicationLoadStatus;
import com.almworks.api.container.RootContainer;
import com.almworks.api.platform.ComponentDescriptor;

public class LoadStatusDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer registrator) {
    registrator.registerActorClass(SplashHider.ROLE, SplashHider.class);
    registrator.registerActorClass(ApplicationLoadStatus.ROLE, ApplicationLoadStatusImpl.class);
  }
}
