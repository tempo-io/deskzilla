package com.almworks.items.sync;

import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DBReader;
import org.jetbrains.annotations.*;

import java.util.Collection;

public interface ItemDiff {

  boolean hasChanges();

  Collection<? extends DBAttribute<?>> getChanged();

  DBReader getReader();

  long getItem();

  @Nullable
  <T> T getNewerValue(DBAttribute<? extends T> attribute);

  @Nullable
  <T> T getElderValue(DBAttribute<? extends T> attribute);

  boolean isChanged(DBAttribute<?> attribute);

  boolean hasHistory();

  ItemVersion getNewerVersion();

  ItemVersion getElderVersion();
}
