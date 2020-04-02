package com.almworks.api.application.util;

import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.ModelKey;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.models.TableColumnAccessor;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public class KeySetBuilder {
  private final List<KeyBuilder<?>> myKeys = Collections15.arrayList();
  private final Map<DBAttribute, KeyBuilder<?>> myKeyMap = Collections15.hashMap();
  private Comparator<LoadedItem> myDefaultOrder = null;
  private ModelKey<Boolean> myDummyKey = null;
  private List<TableColumnAccessor<LoadedItem, ?>> myAdditionalColumns;

  public List<ModelKey<?>> getKeys() {
    List<ModelKey<?>> result = Collections15.arrayList(myKeys.size());
    for (KeyBuilder<?> builder : myKeys)
      result.add(builder.getKey());
    return result;
  }

  public Map<DBAttribute, ModelKey<?>> getKeyMap() {
    HashMap<DBAttribute, ModelKey<?>> r = Collections15.hashMap(myKeyMap.size());
    for (Map.Entry<DBAttribute, KeyBuilder<?>> e : myKeyMap.entrySet()) {
      r.put(e.getKey(), e.getValue().getKey());
    }
    return r;
  }

  public <T extends Comparable<T>> void setDefaultOrderKey(BaseKeyBuilder<T> builder) {
    myDefaultOrder = LoadedItemComparator.create(builder.getKey());
  }

  public Comparator<LoadedItem> getDefaultOrder() {
    return myDefaultOrder;
  }

  public void setDummyKey(ModelKey<Boolean> dummyKey) {
    myDummyKey = dummyKey;
  }

  public List<? extends TableColumnAccessor<LoadedItem, ?>> getColumns() {
    assert myDefaultOrder != null;
    List<TableColumnAccessor<LoadedItem, ?>> result = Collections15.arrayList();
    for (KeyBuilder<?> builder : myKeys) {
      TableColumnAccessor<LoadedItem, ?> column = createColumn(builder);
      if (column != null)
        result.add(column);
    }
    if (myAdditionalColumns != null)
      result.addAll(myAdditionalColumns);
    return result;
  }

  public void addColumn(TableColumnAccessor<LoadedItem, ?> column) {
    if (myAdditionalColumns == null)
      myAdditionalColumns = Collections15.arrayList();
    myAdditionalColumns.add(column);
  }

  @Nullable
  public TableColumnAccessor<LoadedItem, ?> createColumn(KeyBuilder<?> builder) {
    return builder.getColumn(myDummyKey, myDefaultOrder);
  }

  public <B extends KeyBuilder<?>> B addKeyBuilder(B builder, DBAttribute pointer) {
    myKeys.add(builder);
    if (pointer != null) {
      myKeyMap.put(pointer, builder);
    }
    return builder;
  }
}
