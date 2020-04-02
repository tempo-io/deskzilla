package com.almworks.items.impl;

import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Log;

import java.util.HashMap;
import java.util.Map;

public class InconsistentReader implements DBReader {
  static final ThreadLocal<DBReader> RUNNING_READER = new ThreadLocal<DBReader>();

  private final SQLiteDatabase myDatabase;

  public InconsistentReader(SQLiteDatabase database) {
    myDatabase = database;
  }

  public <T> T getValue(final long item, final DBAttribute<T> attribute) {
    return myDatabase.read(DBPriority.BACKGROUND, new ReadTransaction<T>() {
      public T transaction(DBReader reader) {
        return reader.getValue(item, attribute);
      }
    }).waitForCompletion();
  }

  public DBQuery query(BoolExpr<DP> expr) {
    assert false;
    return null;
  }

  @Override
  public long getItemIcn(final long item) {
    return myDatabase.read(DBPriority.BACKGROUND, new ReadTransaction<Long>() {
      public Long transaction(DBReader reader) {
        return reader.getItemIcn(item);
      }
    }).waitForCompletion();
  }

  @Override
  public long getTransactionIcn() {
    return myDatabase.read(DBPriority.BACKGROUND, new ReadTransaction<Long>() {
      public Long transaction(DBReader reader) {
        return reader.getTransactionIcn();
      }
    }).waitForCompletion();
  }

  public long findMaterialized(final DBIdentifiedObject object) {
    Long artifact = myDatabase.read(DBPriority.BACKGROUND, new ReadTransaction<Long>() {
      public Long transaction(DBReader reader) {
        return reader.findMaterialized(object);
      }
    }).waitForCompletion();
    return artifact == null ? 0 : artifact;
  }

  public long assertMaterialized(DBIdentifiedObject object) {
    long v = findMaterialized(object);
    assert v != 0 : object;
    return v;
  }

  @Override
  public AttributeMap getAttributeMap(final long item) {
    return myDatabase.readBackground(new ReadTransaction<AttributeMap>() {
      public AttributeMap transaction(DBReader reader) {
        return reader.getAttributeMap(item);
      }
    }).waitForCompletion();
  }

  public DBAttribute getAttribute(final String id) {
    return myDatabase.read(DBPriority.BACKGROUND, new ReadTransaction<DBAttribute>() {
      public DBAttribute transaction(DBReader reader) {
        return reader.getAttribute(id);
      }
    }).waitForCompletion();
  }

  @Override
  public LongList getChangedItemsSorted(long fromIcn) {
    throw new UnsupportedOperationException("cannot provide consistent list of changed items outside transaction");
  }

  @Override
  public long getTransactionTime() {
    Log.error("No transaction: inconsistent reader " + this);
    return System.currentTimeMillis();
  }

  @Override
  public Map getTransactionCache() {
    Log.error("No transaction: inconsistent reader " + this);
    return new HashMap();
  }
}
