package com.almworks.explorer.loader;

import com.almworks.api.application.*;
import com.almworks.items.api.DBReader;

/**
 * @author dyoma
 */
class DBStatusHolder {
  private final ItemWrapper myItem;
  private LoadedItem.DBStatus myStatus;

  public DBStatusHolder(ItemWrapper item) {
    myItem = item;
  }

  public void updateStatus(DBReader reader) {
    LoadedItem.DBStatus newStatus = DBStatusKey.calcStatus(myItem.getItem(), reader, myItem.getConnection());
    synchronized (this) {
      myStatus = newStatus;
    }
  }

  public LoadedItem.DBStatus getStatus() {
    synchronized (this) {
      return myStatus;
    }
  }

  public void setStatus(LoadedItem.DBStatus status) {
    synchronized (this) {
      myStatus = status;
    }
  }
}
