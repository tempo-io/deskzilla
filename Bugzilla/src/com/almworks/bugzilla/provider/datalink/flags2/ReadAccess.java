package com.almworks.bugzilla.provider.datalink.flags2;

import com.almworks.api.engine.Engine;
import com.almworks.bugzilla.provider.*;
import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.exec.Context;
import org.almworks.util.*;
import org.jetbrains.annotations.*;

import java.util.List;

public abstract class ReadAccess implements VersionSource {
  private final long myConnection;
  private static final TypedKey<ReadAccess> KEY = TypedKey.create("connectionWrite");
  private final BugzillaContext myContext;

  protected ReadAccess(long connection, BugzillaContext context) {
    myConnection = connection;
    myContext = context;
  }

  public static ReadAccess getInstance(final ItemVersion bug) {
    ReadAccess existing = getExisting(bug.getReader());
    if (existing == null) {
      Long c = bug.getValue(SyncAttributes.CONNECTION);
      BugzillaConnection connection;
      if (c != null)
        connection = Util.castNullable(BugzillaConnection.class,
          Context.require(Engine.ROLE).getConnectionManager().findByItem(c));
      else connection = null;
      if (connection == null) throw new IllegalArgumentException("No connection " + bug.getItem() + " " + c);
      BugzillaContextImpl context = connection.getContext();
      if (context == null) Log.error("Wrong connection " + bug + " " + c + " " + connection);
      ReadAccess created = new ReadAccess(c, context) {
        @Override
        public VersionSource getVersionSource() {
          return bug;
        }
      };
      replaceInstance(bug.getReader(), existing, created);
      existing = created;
    }
    return existing;
  }

  @NotNull
  @Override
  public ItemVersion forItem(long item) {
    return getVersionSource().forItem(item);
  }

  @Override
  @NotNull
  public ItemVersion forItem(DBIdentifiedObject object) {
    return getVersionSource().forItem(object);
  }

  @Override
  public long findMaterialized(DBIdentifiedObject object) {
    return getVersionSource().findMaterialized(object);
  }

  @Nullable
  protected static ReadAccess getExisting(DBReader reader) {
    return KEY.getFrom(reader.getTransactionCache());
  }

  protected static void replaceInstance(DBReader reader, ReadAccess old, ReadAccess replacement) {
    if (replacement == null) return;
    if (old != getExisting(reader)) Log.error("Wrong replacement");
    if (old != null && old.getConnectionItem() != replacement.getConnectionItem()) Log.error("Wrong connection");
    KEY.putTo(reader.getTransactionCache(), replacement);
  }

  public LongList findAllOfType(DBItemType itemType) {
    BoolExpr<DP> type = DPEqualsIdentified.create(DBAttribute.TYPE, itemType);
    BoolExpr<DP> connection = DPEqualsIdentified.create(SyncAttributes.CONNECTION, myContext.getPrivateMetadata().thisConnection);
    return getReader().query(BoolExpr.and(type, connection)).copyItemsSorted();
  }

  public long getConnectionItem() {
    return myConnection;
  }

  public BugzillaContext getContext() {
    return myContext;
  }

  @Override
  @NotNull
  public DBReader getReader() {
    return getVersionSource().getReader();
  }

  @NotNull
  @Override
  public List<ItemVersion> readItems(LongList items) {
    return getVersionSource().readItems(items);
  }

  public abstract VersionSource getVersionSource();

  public ItemVersion read(long item) {
    return getVersionSource().forItem(item);
  }

  public <T> T getValue(long item, DBAttribute<T> attribute) {
    return read(item).getValue(attribute);
  }

  public PrivateMetadata getPrivateMD() {
    return myContext.getPrivateMetadata();
  }
}
