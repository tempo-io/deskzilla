package com.almworks.items.impl;

import com.almworks.integers.LongList;
import com.almworks.items.api.*;
import com.almworks.items.impl.sqlite.Schema;
import com.almworks.items.impl.sqlite.TransactionContext;
import com.almworks.items.util.AttributeMap;
import com.almworks.sqlite4java.*;
import com.almworks.util.bool.BoolExpr;
import gnu.trove.TLongLongHashMap;
import org.almworks.util.Log;
import org.almworks.util.TypedKey;

import java.util.Map;
import java.util.Set;

public class DBReaderImpl implements DBReader {
  private static final TypedKey<TLongLongHashMap> ITEM_ICNS = TypedKey.create("itemIcns");
  protected final TransactionContext myContext;

  private static final SQLParts GET_ITEM_ICN_SQL =
    new SQLParts().append("SELECT ").append(Schema.ITEMS_LAST_ICN.getName()).append(" FROM ").append(Schema.ITEMS).
      append(" WHERE ").append(Schema.ITEMS_ITEM.getName()).append(" = ?");

  public DBReaderImpl(TransactionContext context) {
    myContext = context;
  }

  public AttributeAdapter getAttributeAdapter(DBAttribute<?> attribute) {
    return myContext.getDatabaseContext().getAttributeAdapter(attribute);
  }

  public <T> T getValue(long item, DBAttribute<T> attribute) {
    try {
      AttributeAdapter adapter = getAttributeAdapter(attribute);
      return (T) adapter.readValue(item, this);
    } catch (SQLiteException e) {
      throw new DBException(e);
    } catch (ClassCastException e) {
      Log.error(e);
      return null;
    }
  }

  public com.almworks.items.api.DBQuery query(BoolExpr<DP> expr) {
    return new DBQueryImpl(this, expr);
  }

  @Override
  public long getItemIcn(long item) {
    TLongLongHashMap icns = ITEM_ICNS.getFrom(getTransactionCache());
    if (icns == null) {
      icns = new TLongLongHashMap();
      ITEM_ICNS.putTo(getTransactionCache(), icns);
    }
    long icn = icns.get(item);
    if (icn > 0) return icn;
    icn = getItemIcnNoCache(item);
    icns.put(item, icn);
    return icn;
  }

  protected final long getItemIcnNoCache(long item) {
    try {
      SQLiteStatement st = myContext.prepare(GET_ITEM_ICN_SQL);
      try {
        st.bind(1, item);
        return st.step() ? st.columnLong(0) : 0;
      } finally {
        st.dispose();
      }
    } catch (SQLiteException e) {
      throw new DBException(e);
    }
  }

  @Override
  public long getTransactionIcn() {
    try {
      return myContext.getIcn();
    } catch (SQLiteException e) {
      throw new DBException(e);
    }
  }

  @Override
  public LongList getChangedItemsSorted(long fromIcn) {
    try {
      return myContext.getChangedItemsSorted(fromIcn);
    } catch (SQLiteException e) {
      throw new DBException(e);
    }
  }

  @Override
  public long getTransactionTime() {
    return myContext.getTransactionTime();
  }

  public long findMaterialized(DBIdentifiedObject object) {
    if (object == null)
      return 0;
    return IdentifiedObjectCache.get(myContext).getMaterialized(object, myContext);
  }

  public long assertMaterialized(DBIdentifiedObject object) {
    long r = findMaterialized(object);
    if (r == 0) {
      throw new IllegalStateException("no " + object);
    }
    return r;
  }

  @Override
  public AttributeMap getAttributeMap(long item) {
    AttributeMap r = new AttributeMap();
    Set<DBAttribute> attributes = AttributeCache.getAttributes(myContext);
    for (DBAttribute attribute : attributes) {
      Object value = getValue(item, attribute);
      if (value != null) {
        r.put(attribute, value);
      }
    }
    return r;
  }

  public DBAttribute getAttribute(String id) {
    return AttributeCache.get(myContext).getAttributeById(id, myContext);
  }

  @Override
  public Map getTransactionCache() {
    return myContext.getTransactionCache();
  }

  public TransactionContext getContext() {
    return myContext;
  }
}
