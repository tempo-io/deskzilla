package com.almworks.engine.items;

import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.bool.BoolExpr;
import org.jetbrains.annotations.*;

import java.util.Map;

/**
 * Note: this class does not extend {@link com.almworks.items.util.DelegatingReader} intentionally. The aim is to provide a reminder to wrap new methods added to {@link DBReader}.
 * */
class DBReaderWrapper implements DBReader {
  private final DBReader myReader;

  DBReaderWrapper(@NotNull DBReader reader) {
    myReader = reader;
  }

  @Override
  public DBQuery query(BoolExpr<DP> expr) {
    BoolExpr<DP> wrappedExpr = ItemStorageAdaptor.wrapExpr(expr);
    DBQuery query = myReader.query(wrappedExpr);
    return new DBQueryWrapper(this, query);
  }

  @Override
  public <T> T getValue(long item, DBAttribute<T> attribute) {
    return myReader.getValue(item, attribute);
  }

  @Override
  public long getTransactionIcn() {
    return myReader.getTransactionIcn();
  }

  @Override
  public long getItemIcn(long item) {
    return myReader.getItemIcn(item);
  }

  @Override
  public long findMaterialized(DBIdentifiedObject object) {
    return myReader.findMaterialized(object);
  }

  @Override
  public long assertMaterialized(DBIdentifiedObject object) {
    return myReader.assertMaterialized(object);
  }

  @Override
  public AttributeMap getAttributeMap(long item) {
    return myReader.getAttributeMap(item);
  }

  @Override
  public DBAttribute getAttribute(String id) {
    return myReader.getAttribute(id);
  }

  @Override
  public Map getTransactionCache() {
    return myReader.getTransactionCache();
  }

  @Override
  public LongList getChangedItemsSorted(long fromIcn) {
    return myReader.getChangedItemsSorted(fromIcn);
  }

  @Override
  public long getTransactionTime() {
    return myReader.getTransactionTime();
  }
}
