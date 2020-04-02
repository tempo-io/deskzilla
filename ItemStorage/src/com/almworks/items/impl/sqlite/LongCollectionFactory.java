package com.almworks.items.impl.sqlite;

import com.almworks.integers.*;
import com.almworks.integers.optimized.SameValuesLongList;
import com.almworks.integers.optimized.SegmentedLongArray;

public abstract class LongCollectionFactory {
  private static volatile LongCollectionFactory instance = new DefaultLongCollectionFactory();

  public static WritableLongList createList() {
    return instance._createList(0);
  }

  public static WritableLongList createList(int allocateSize) {
    return instance._createList(allocateSize);
  }

  public static WritableLongList createSameValuesList(int allocateSize) {
    return instance._createSameValuesList(allocateSize);
  }

  public static ListLongMap createListLongMap(int allocateSize) {
    return instance._createListLongMap(allocateSize);
  }

  public static void dispose(Object collection) {
    instance._dispose(collection);
  }

  public static void setFactory(LongCollectionFactory factory) {
    if (factory == null)
      throw new NullPointerException();
    instance = factory;
  }

  protected abstract WritableLongList _createList(int allocateSize);

  protected abstract WritableLongList _createSameValuesList(int allocateSize);

  protected abstract ListLongMap _createListLongMap(int allocateSize);

  protected abstract void _dispose(Object collection);


  public static class DefaultLongCollectionFactory extends LongCollectionFactory {
    protected WritableLongList _createList(int allocateSize) {
      // todo initialize array with segment size corresponding to allocateSize
      return new SegmentedLongArray();
    }

    protected WritableLongList _createSameValuesList(int allocateSize) {
      // todo - was ListIntMap, which is now removed for SameValuesIntList
      return new SameValuesLongList(new IntLongMap());
    }

    protected ListLongMap _createListLongMap(int allocateSize) {
      return new ListLongMap(_createList(allocateSize));
    }

    protected void _dispose(Object collection) {
      if (collection instanceof WritableLongList) {
        ((WritableLongList) collection).clear();
      } else if (collection instanceof ListLongMap) {
        ((ListLongMap) collection).clear();
      } 
    }
  }
}
