package com.almworks.bugzilla.provider.timetrack;

import com.almworks.api.container.RootContainer;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.timetrack.api.TimeTrackingCustomizer;

public class BugzillaTimeTrackingDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer container) {
     container.registerActor(TimeTrackingCustomizer.ROLE, new BugzillaTimeTrackingCustomizer());
  }
}
