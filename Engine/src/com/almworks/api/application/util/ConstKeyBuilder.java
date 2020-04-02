package com.almworks.api.application.util;

import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.ModelKey;
import com.almworks.util.models.TableColumnAccessor;
import org.jetbrains.annotations.*;

import java.util.Comparator;

public class ConstKeyBuilder<T> implements KeyBuilder<T> {
  private final ModelKey<T> myKey;

  public ConstKeyBuilder(ModelKey<T> key) {
    myKey = key;
  }

  @NotNull
  public ModelKey<T> getKey() {
    return myKey;
  }

  @Nullable
  public TableColumnAccessor<LoadedItem, ?> getColumn(ModelKey<Boolean> dummyKey,
    Comparator<LoadedItem> defaultOrder)
  {
    return null;
  }
}
