package com.almworks.actions.create;

import com.almworks.api.edit.CreateItemPolicy;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.MainMenu;
import com.almworks.api.gui.WindowController;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.util.*;
import com.almworks.util.images.Icons;
import com.almworks.util.properties.Role;
import com.almworks.util.threads.Threads;
import com.almworks.util.ui.actions.ActionRegistry;
import org.almworks.util.Collections15;

import java.util.Map;

/**
 * @author : Dyoma
 */
public class CreateItemHelper {
  private final Map<Pair<Connection, DBIdentifiedObject>, WindowController> myNewWindows = Collections15.hashMap();
  public static final Role<CreateItemHelper> ROLE = Role.role(CreateItemHelper.class);

  public WindowController getNewItemWindow(Connection connection, DBIdentifiedObject type) {
    Threads.assertAWTThread();
    Pair<Connection, DBIdentifiedObject> key = Pair.create(connection, type);
    return myNewWindows.get(key);
  }

  public void unregisterWindow(Connection connection, DBIdentifiedObject type) {
    Pair<Connection, DBIdentifiedObject> key = Pair.create(connection, type);
    assert myNewWindows.containsKey(key);
    myNewWindows.remove(key);
  }

  public void registerWindow(Connection connection, DBIdentifiedObject type, WindowController window) {
    Pair<Connection, DBIdentifiedObject> key = Pair.create(connection, type);
    assert !myNewWindows.containsKey(key);
    myNewWindows.put(key, window);
  }

  public static void registerActions(ActionRegistry registry) {
    registry.registerAction(MainMenu.Edit.NEW_ITEM, CreateWindowCreatorDescriptor.createCreateAction(
      "New &" + Terms.ref_Artifact, L.tooltip("Create a new " + Terms.ref_artifact), Icons.ACTION_CREATE_NEW_ITEM,
      CreateItemPolicy.DEFAULT));
  }
}
