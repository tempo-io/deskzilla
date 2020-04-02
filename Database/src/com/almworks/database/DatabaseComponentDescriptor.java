package com.almworks.database;

import com.almworks.api.container.RootContainer;
import com.almworks.api.database.Workspace;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.database.gui.InconsistencyInquiryHandler;
import com.almworks.util.properties.Role;

/**
 * :todoc:
 *
 * @author sereda
 */
public class DatabaseComponentDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer registrator) {
    registrator.registerActorClass(Workspace.ROLE, WorkspaceImpl.class);
    registrator.registerActorClass(Role.role("inconsistencyInquiryHandler"), InconsistencyInquiryHandler.class);
  }
}
