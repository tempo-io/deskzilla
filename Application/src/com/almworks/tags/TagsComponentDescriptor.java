package com.almworks.tags;

import com.almworks.api.container.RootContainer;
import com.almworks.api.platform.ComponentDescriptor;

public class TagsComponentDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer container) {
    container.registerActorClass(TagsComponentImpl.ROLE, TagsComponentImpl.class);
    container.registerActorClass(ImportTagsOnFirstRun.ROLE, ImportTagsOnFirstRun.class);
  }
}
