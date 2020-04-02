package com.almworks.items.sync.impl;

import com.almworks.integers.LongList;
import com.almworks.items.api.DBIdentifiedObject;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.VersionSource;
import com.almworks.items.sync.util.IllegalItem;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * @author dyoma
 */
public abstract class BasicVersionSource implements VersionSource {
  @NotNull
  @Override
  public ItemVersion forItem(DBIdentifiedObject object) {
    long item = findMaterialized(object);
    return item > 0 ? forItem(item) : new IllegalItem(this, item);
  }

  @Override
  public long findMaterialized(DBIdentifiedObject object) {
    return getReader().findMaterialized(object);
  }

  @NotNull
  @Override
  public List<ItemVersion> readItems(LongList items) {
    if (items == null || items.isEmpty()) return Collections.emptyList();
    final long[] array = items.toNativeArray();
    return new AbstractList<ItemVersion>() {
      @Override
      public ItemVersion get(int index) {
        return BasicVersionSource.this.forItem(array[index]);
      }

      @Override
      public int size() {
        return array.length;
      }
    };
  }
}
