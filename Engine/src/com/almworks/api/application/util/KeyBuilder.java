package com.almworks.api.application.util;

import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.ModelKey;
import com.almworks.util.models.TableColumnAccessor;
import org.jetbrains.annotations.*;

import java.util.Comparator;

public interface KeyBuilder<T> {
  @NotNull
  ModelKey<T> getKey();

  @Nullable
  TableColumnAccessor<LoadedItem, ?> getColumn(ModelKey<Boolean> dummyKey,
    Comparator<LoadedItem> defaultOrder);
}
