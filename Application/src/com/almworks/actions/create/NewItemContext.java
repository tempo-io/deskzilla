package com.almworks.actions.create;

import com.almworks.api.application.DBDataRoles;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.WindowManager;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.api.DBItemType;
import com.almworks.util.properties.Role;
import com.almworks.util.ui.actions.*;

import java.util.Collection;

/**
 * @author : Dyoma
 */
class NewItemContext {
  private final ActionContext myContext;

  public NewItemContext(ActionContext context) {
    myContext = context;
  }

  public CreateItemHelper getCreateArtifactHelper() throws CantPerformException {
    return findComponent(CreateItemHelper.ROLE);
  }

  private <T> T findComponent(Role<T> role) throws CantPerformException {
    return myContext.getSourceObject(role);
  }

  public Connection getConnection() throws CantPerformException {
    return DBDataRoles.findConnection(myContext);
  }

  public WindowManager getWindowManager() throws CantPerformException {
    return findComponent(WindowManager.ROLE);
  }

  public DBIdentifiedObject getItemType() throws CantPerformException {
    Collection<DBItemType> types = getConnection().getPrimaryTypes();
    assert !types.isEmpty();
    CantPerformException.ensure(!types.isEmpty());
    return types.iterator().next();
  }

  public static void watch(UpdateRequest context) {
    DBDataRoles.watchConnection(context);
  }
}
