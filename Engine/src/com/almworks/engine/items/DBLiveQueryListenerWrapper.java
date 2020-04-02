package com.almworks.engine.items;

import com.almworks.items.api.*;

import static com.almworks.engine.items.DatabaseWrapperPrivateUtil.wrapReader;

class DBLiveQueryListenerWrapper implements DBLiveQuery.Listener {
  private final DBLiveQuery.Listener myListener;

  public DBLiveQueryListenerWrapper(DBLiveQuery.Listener listener) {
    myListener = listener;
  }

  @Override
  public void onICNPassed(long icn) {
    myListener.onICNPassed(icn);
  }

  @Override
  public void onDatabaseChanged(DBEvent event, DBReader reader) {
    DBReaderWrapper wrappedReader = wrapReader(reader);
    myListener.onDatabaseChanged(event, wrappedReader);
  }
}
