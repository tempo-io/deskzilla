package com.almworks.engine;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBWriter;
import com.almworks.items.util.SyncAttributes;

public class TestPrivateMeta {
  public final long connectionItem;
  public final long prototypeItem;

  public TestPrivateMeta(DBWriter writer) {
    connectionItem = writer.nextItem();
    writer.setValue(connectionItem, DBAttribute.TYPE, writer.materialize(TestCommonMeta.CONNECTION));

    prototypeItem = writer.nextItem();
    writer.setValue(prototypeItem, DBAttribute.TYPE, writer.materialize(TestCommonMeta.NOTE));
    writer.setValue(prototypeItem, SyncAttributes.IS_PROTOTYPE, Boolean.TRUE);
    writer.setValue(prototypeItem, SyncAttributes.CONNECTION, connectionItem);
  }
}
