package com.almworks.bugzilla.provider;

import com.almworks.api.container.RootContainer;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.bugzilla.provider.qb.BugzillaCustomParserProvider;
import com.almworks.bugzilla.provider.workflow.BugzillaEditPrimitiveFactory;
import com.almworks.explorer.qbuilder.filter.CustomParserProvider;
import com.almworks.explorer.workflow.CustomEditPrimitiveFactory;
import com.almworks.util.properties.Role;

/**
 * :todoc:
 *
 * @author sereda
 */
public class BugzillaComponentDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer registrator) {
    registrator.registerActorClass(Role.role("bz"), BugzillaProvider.class);
    registrator.registerActorClass(Role.role(CustomEditPrimitiveFactory.class), BugzillaEditPrimitiveFactory.class);
    registrator.registerActorClass(Role.role(CustomParserProvider.class), BugzillaCustomParserProvider.class);
  }
}
