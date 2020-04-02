package com.almworks.engine.items;

import com.almworks.items.api.*;

import static com.almworks.engine.items.DatabaseWrapperPrivateUtil.wrapReader;

class DBListenerWrapper implements DBListener {
  private final DBListener myListener;

  public DBListenerWrapper(DBListener listener) {
    myListener = listener;
  }

  @Override
  public void onDatabaseChanged(DBEvent event, DBReader reader) {
    DBReaderWrapper wrappedReader = wrapReader(reader);
    myListener.onDatabaseChanged(event, wrappedReader);
  }
}
