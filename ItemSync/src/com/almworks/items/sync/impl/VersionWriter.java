package com.almworks.items.sync.impl;

import com.almworks.integers.LongArray;
import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.sync.ItemProxy;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.edit.BaseDBDrain;
import com.almworks.items.util.SyncAttributes;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

class VersionWriter extends BaseVersionWriter {
  private final VersionHolder.Write myHolder;
  private boolean myShadowableChanged = false;

  public VersionWriter(BaseDBDrain drain, VersionHolder.Write holder) {
    super(drain);
    myHolder = holder;
  }

  @Override
  public long getItem() {
    return myHolder.getItem();
  }

  @Override
  public <T> void setValue(DBAttribute<T> attribute, T value) {
    boolean changed = myHolder.setValue(attribute, value);
    if (changed) myShadowableChanged = myShadowableChanged || SyncSchema.hasShadowableValue(getReader(), attribute);
  }

  @Override
  public void setValue(DBAttribute<Long> attribute, DBIdentifiedObject value) {
    long item = materialize(value);
    if (item > 0) setValue(attribute, item);
    else setValue(attribute, (Long)null);
  }

  @Override
  public void setValue(DBAttribute<Long> attribute, ItemProxy value) {
    long item = materialize(value);
    if (item > 0) setValue(attribute, item);
    else setValue(attribute, (Long) null);
  }

  @Override
  public void setValue(DBAttribute<Long> attribute, @Nullable ItemVersion value) {
    if (value != null) {
      long item = value.getItem();
      if (item > 0) {
        setValue(attribute, item);
        return;
      }
    }
    setValue(attribute, (Long)null);
  }

  @Override
  public void setList(DBAttribute<List<Long>> attribute, long[] value) {
    List<Long> list;
    if (value == null || value.length == 0) list = null;
    else {
      list = Collections15.arrayList(value.length);
      for (long l : value) list.add(l);
    }
    setValue(attribute, list);
  }

  @Override
  public void delete() {
    setValue(SyncSchema.INVISIBLE, true);
  }

  @Override
  public void setAlive() {
    setValue(SyncSchema.INVISIBLE, null);
    setValue(SyncAttributes.EXISTING, true);
  }

  @Override
  public void setSet(DBAttribute<? extends Collection<? extends Long>> attribute, LongList value) {
    DBAttribute.ScalarComposition composition = attribute.getComposition();
    boolean composeSet;
    switch (composition) {
    case SET: composeSet = true; break;
    case LIST: composeSet = false; break;
    default:
      Log.error("Wrong composition " + composition + " " + attribute);
      return;
    }
    long[] array = value != null ? value.toNativeArray() : null;
    Collection<Long> set;
    if (value == null || value.isEmpty()) set = null;
    else {
      set = composeSet ? Collections15.<Long>hashSet(array.length) : Collections15.<Long>arrayList(array.length);
      for (int i = 0; i < value.size(); i++) {
        long item = value.get(i);
        if (item > 0) set.add(item);
      }
    }
    //noinspection unchecked
    setValue((DBAttribute<Collection<Long>>)attribute, set);
  }

  @Override
  public void setSet(DBAttribute<? extends Collection<? extends Long>> attribute, Collection<ItemProxy> value) {
    LongArray items = new LongArray();
    for (ItemProxy proxy : value) items.add(proxy.findOrCreate(this));
    setSet(attribute, items);
  }

  @Override
  public VersionHolder getHolder() {
    return myHolder;
  }

  @Override
  public void markMerged() {
    assert false;
  }

  @Override
  public <T extends Collection<? extends Long>> void addValue(DBAttribute<T> attribute, DBIdentifiedObject value) {
    long item = materialize(value);
    if (item > 0) addValue(attribute, item);
  }

  private <T extends Collection<? extends Long>> void addValue(DBAttribute<T> attribute, long item) {
    T current = getValue(attribute);
    if (current == null || current.isEmpty()) setValue(attribute, (T)Collections.singleton(item));
    else {
      ((Collection<Long>) current).add(item);
      setValue(attribute, current);
    }
  }
}
