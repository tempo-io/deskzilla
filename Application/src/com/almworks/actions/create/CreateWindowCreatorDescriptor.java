package com.almworks.actions.create;

import com.almworks.api.container.RootContainer;
import com.almworks.api.edit.CreateItemPolicy;
import com.almworks.api.platform.ComponentDescriptor;
import com.almworks.util.ui.actions.AnAction;

import javax.swing.*;

public class CreateWindowCreatorDescriptor implements ComponentDescriptor {
  public void registerActors(RootContainer registrator) {
    registrator.registerActorClass(CreateItemHelper.ROLE, CreateItemHelper.class);
  }

  public static AnAction createCreateAction(String name, String description, Icon icon, CreateItemPolicy<?> policy) {
    return new NewItemAction(name, description, icon, policy);
  }
}
