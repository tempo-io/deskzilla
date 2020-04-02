package com.almworks.api.application.util;

import com.almworks.api.application.LoadedItem;
import com.almworks.api.application.ModelKey;
import com.almworks.util.collections.Containers;

import java.util.Comparator;

/**
 * @author dyoma
*/
public class LoadedItemComparator<T extends Comparable<T>> implements Comparator<LoadedItem> {
  private final ModelKey<T> myDefaultOrderByKey;

  public LoadedItemComparator(ModelKey<T> defaultOrderByKey) {
    myDefaultOrderByKey = defaultOrderByKey;
  }

  public int compare(LoadedItem o1, LoadedItem o2) {
    T v1 = myDefaultOrderByKey.getValue(o1.getValues());
    T v2 = myDefaultOrderByKey.getValue(o2.getValues());
    int diff;
    if (v1 == null) {
      diff = v2 != null ? 1 : 0;
    } else if (v2 == null) {
      diff = -1;
    } else {
      diff = v1.compareTo(v2);
    }
    if (diff != 0) {
      return diff;
    } else {
      return Containers.compareLongs(o1.getItem(), o2.getItem());
    }
  }

  public static <T extends Comparable<T>> Comparator<LoadedItem> create(ModelKey<T> defaultOrderByKey) {
    return new LoadedItemComparator<T>(defaultOrderByKey);
  }
}
