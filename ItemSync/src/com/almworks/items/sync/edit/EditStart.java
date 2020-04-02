package com.almworks.items.sync.edit;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;

class EditStart implements EditPrepare {
  private final EditCounterpart myCounterpart;
  private final EditorFactory myFactory;
  private final ThreadLocal<DBReader> myReader = new ThreadLocal<DBReader>();

  public EditStart(EditCounterpart counterpart, EditorFactory factory) {
    myCounterpart = counterpart;
    myFactory = factory;
  }

  public EditCounterpart getCounterpart() {
    return myCounterpart;
  }

  public boolean isReleased() {
    return myCounterpart.isReleased();
  }

  @Override
  public LongList getItems() {
    return myCounterpart.getItems();
  }

  @Override
  public EditControl getControl() {
    return myCounterpart;
  }

  @Override
  public boolean addItems(LongList items) {
    DBReader reader = myReader.get();
    if (reader == null) throw new IllegalStateException();
    return myCounterpart.addItems(reader, items);
  }

  public void release() {
    myCounterpart.release();
  }

  public void performStart(DBReader reader) throws DBOperationCancelledException {
    boolean success = false;
    try {
      if (!myCounterpart.beforeStart(reader)) return;
      myReader.set(reader);
      ItemEditor editor = myFactory.prepareEdit(reader, this);
      myReader.set(null);
      if (editor != null) {
        myCounterpart.afterStart(editor);
        success = true;
      }
    } finally {
      myReader.set(null);
      if (!success) myCounterpart.release();
    }
  }
}
