package com.almworks.items.api;

import com.almworks.integers.LongList;
import com.almworks.items.util.AttributeMap;
import com.almworks.util.bool.BoolExpr;
import org.jetbrains.annotations.*;

import java.util.Map;

public interface DBReader {
  <T> T getValue(long item, DBAttribute<T> attribute);

  DBQuery query(BoolExpr<DP> expr);

  long getTransactionIcn();

  long getItemIcn(long item);

  // may return 0
  long findMaterialized(DBIdentifiedObject object);

  // may not return 0
  long assertMaterialized(DBIdentifiedObject object);

  public AttributeMap getAttributeMap(long item);

  @Nullable
  DBAttribute getAttribute(String id);

  Map getTransactionCache();

  LongList getChangedItemsSorted(long fromIcn);

  long getTransactionTime();
}
