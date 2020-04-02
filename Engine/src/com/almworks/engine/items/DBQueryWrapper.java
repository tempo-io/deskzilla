package com.almworks.engine.items;

import com.almworks.integers.*;
import com.almworks.items.api.*;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.commons.LongObjFunction2;

class DBQueryWrapper implements DBQuery {
  private final DBQuery myQuery;
  private final DBReader myReaderWrapper;

  public DBQueryWrapper(DBReader readerWrapper, DBQuery query) {
    myReaderWrapper = readerWrapper;
    myQuery = query;
  }

  @Override
  public DBReader getReader() {
    return myReaderWrapper;
  }

  @Override
  public BoolExpr<DP> getExpr() {
    return myQuery.getExpr();
  }

  @Override
  public DBQuery query(BoolExpr<DP> expr) {
    DBQuery subquery = myQuery.query(expr);
    return new DBQueryWrapper(myReaderWrapper, subquery);
  }

  @Override
  public long count() {
    return myQuery.count();
  }

  @Override
  public LongList distributionCount(DBAttribute<?>... groupAttributes) {
    return myQuery.distributionCount(groupAttributes);
  }

  @Override
  public LongArray copyItemsSorted() {
    return myQuery.copyItemsSorted();
  }

  @Override
  public <C extends LongCollector> C copyItems(C collector) {
    return myQuery.copyItems(collector);
  }

  @Override
  public boolean contains(long item) {
    return myQuery.contains(item);
  }

  @Override
  public boolean filterItems(LongIterable items, LongCollector result) {
    return myQuery.filterItems(items, result);
  }

  @Override
  public LongArray filterItemsSorted(LongIterable items) {
    return myQuery.filterItemsSorted(items);
  }

  @Override
  public long getItem() {
    return myQuery.getItem();
  }

  @Override
  public <T> long getItemByKey(DBAttribute<T> attribute, T value) {
    return myQuery.getItemByKey(attribute, value);
  }

  @Override
  public <T> T fold(T seed, LongObjFunction2<T> f) {
    return myQuery.fold(seed, f);
  }
}
