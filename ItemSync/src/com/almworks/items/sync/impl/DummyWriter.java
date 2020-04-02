package com.almworks.items.sync.impl;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.sync.ItemProxy;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.edit.BaseDBDrain;
import com.almworks.items.util.SyncAttributes;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.Collection;
import java.util.List;

class DummyWriter extends BaseVersionWriter  {
  private VersionHolder myHolder;
  private boolean myMessageLogged = false;

  public DummyWriter(BaseDBDrain drain, VersionHolder holder) {
    super(drain);
    myHolder = holder;
  }

  @Override
  public VersionHolder getHolder() {
    return myHolder;
  }

  @Override
  public long getItem() {
    return myHolder.getItem();
  }

  @Override
  public <T> void setValue(DBAttribute<T> attribute, T value) {
    logDummyMessage();
  }

  private void logDummyMessage() {
    if (myMessageLogged) return;
    Log.warn("DummyWriter: No data written to DB " + getItem());
    myMessageLogged = true;
  }

  @Override
  public void setValue(DBAttribute<Long> attribute, DBIdentifiedObject value) {
    logDummyMessage();
  }

  @Override
  public void setValue(DBAttribute<Long> attribute, ItemProxy value) {
    logDummyMessage();
  }

  @Override
  public void setValue(DBAttribute<Long> attribute, @Nullable ItemVersion value) {
    logDummyMessage();
  }

  @Override
  public void setList(DBAttribute<List<Long>> attribute, long[] value) {
    logDummyMessage();
  }

  @Override
  public void delete() {
    logDummyMessage();
  }

  @Override
  public void setAlive() {
    if (isInvisible() || getValue(SyncAttributes.EXISTING) == null) logDummyMessage();
  }

  @Override
  public void setSet(DBAttribute<? extends Collection<? extends Long>> attribute, LongList value) {
    logDummyMessage();
  }

  @Override
  public void setSet(DBAttribute<? extends Collection<? extends Long>> attribute, Collection<ItemProxy> value) {
    logDummyMessage();
  }

  @Override
  public void markMerged() {
    assert false;
  }

  @Override
  public <T extends Collection<? extends Long>> void addValue(DBAttribute<T> attribute, DBIdentifiedObject value) {
    logDummyMessage();
  }

  @Override
  public String toString() {
    return "DummyWriter " + toStringItemInfo();
  }
}
