package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.sync.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.commons.Procedure;
import com.almworks.util.exec.ThreadGate;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.List;

public class WriteAccess extends ReadAccess implements DBDrain {
  private final DBDrain myDrain;

  private WriteAccess(DBDrain drain, long connection, BugzillaContext context) {
    super(connection, context);
    myDrain = drain;
  }

  @NotNull
  @Override
  public ItemVersion forItem(DBIdentifiedObject object) {
    return myDrain.forItem(object);
  }

  @Override
  public VersionSource getVersionSource() {
    return myDrain;
  }

  @Override
  public ItemVersionCreator changeItem(long item) {
    return myDrain.changeItem(item);
  }

  @Override
  public List<ItemVersionCreator> changeItems(LongList items) {
    return myDrain.changeItems(items);
  }

  @Override
  public void finallyDo(ThreadGate gate, Procedure<Boolean> procedure) {
    myDrain.finallyDo(gate, procedure);
  }

  @Override
  public SyncManager getManager() {
    return myDrain.getManager();
  }

  @Override
  public ItemVersionCreator changeItem(DBIdentifiedObject obj) {
    return myDrain.changeItem(obj);
  }

  @Override
  public ItemVersionCreator changeItem(ItemProxy proxy) {
    return myDrain.changeItem(proxy);
  }

  @Override
  public ItemVersionCreator createItem() {
    return myDrain.createItem();
  }

  @Override
  public long materialize(ItemProxy object) {
    return myDrain.materialize(object);
  }

  @Override
  public long materialize(DBIdentifiedObject object) {
    return myDrain.materialize(object);
  }

  public static WriteAccess getInstance(ItemVersionCreator bug, BugzillaContext context) {
    DBReader reader = bug.getReader();
    ReadAccess existing = getExisting(reader);
    if (existing instanceof WriteAccess) return (WriteAccess) existing;
    long connection = bug.materialize(context.getConnection().getConnectionRef());
    if (connection <= 0) Log.error("Illegal connection");
    WriteAccess access = new WriteAccess(bug, connection, context);
    replaceInstance(reader, existing, access);
    return access;
  }

  public ItemVersionCreator change(long item) {
    ItemVersionCreator creator = myDrain.changeItem(item);
    creator.setAlive();
    return creator;
  }

  public ItemVersionCreator create(DBItemType type) {
    ItemVersionCreator item = myDrain.createItem();
    item.setValue(DBAttribute.TYPE, type);
    item.setValue(SyncAttributes.CONNECTION, getConnectionItem());
    return item;
  }
}
