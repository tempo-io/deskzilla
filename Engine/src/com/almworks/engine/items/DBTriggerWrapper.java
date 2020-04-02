package com.almworks.engine.items;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBTrigger;
import com.almworks.items.api.DBWriter;

import static com.almworks.engine.items.DatabaseWrapperPrivateUtil.wrapWriter;
import static com.almworks.engine.items.ItemStorageAdaptor.wrapExpr;

class DBTriggerWrapper extends DBTrigger {
  private final DBTrigger myTrigger;

  public DBTriggerWrapper(DBTrigger trigger) {
    super(trigger.getId(), wrapExpr(trigger.getExpr()));
    myTrigger = trigger;
  }

  @Override
  public void apply(LongList itemsSorted, DBWriter writer) {
    DBWriterWrapper wrappedWriter = wrapWriter(writer);
    myTrigger.apply(itemsSorted, wrappedWriter);
  }
}
