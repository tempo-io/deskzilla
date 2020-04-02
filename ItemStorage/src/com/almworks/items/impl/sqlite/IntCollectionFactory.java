package com.almworks.items.impl.sqlite;

import com.almworks.integers.*;
import com.almworks.integers.optimized.*;

public abstract class IntCollectionFactory {
  private static volatile IntCollectionFactory instance = new DefaultIntCollectionFactory();

  public static WritableIntList createList() {
    return instance._createList(0);
  }

  public static WritableLongList createLongList() {
    return new SegmentedLongArray();
  }

  public static WritableLongList createLongList(int allocateSize) {
    return new SegmentedLongArray();
  }

  public static WritableIntList createList(int allocateSize) {
    return instance._createList(allocateSize);
  }

  public static WritableIntList createSameValuesList(int allocateSize) {
    return instance._createSameValuesList(allocateSize);
  }

  public static ListIntMap createListIntMap(int allocateSize) {
    return instance._createListIntMap(allocateSize);
  }

  public static ListLongMap createListLongMap(int allocateSize) {
    return new ListLongMap(createLongList(allocateSize));
  }

  public static void dispose(Object collection) {
    instance._dispose(collection);
  }

  public static void setFactory(IntCollectionFactory factory) {
    if (factory == null)
      throw new NullPointerException();
    instance = factory;
  }

  protected abstract WritableIntList _createList(int allocateSize);

  protected abstract WritableIntList _createSameValuesList(int allocateSize);

  protected abstract ListIntMap _createListIntMap(int allocateSize);

  protected abstract void _dispose(Object collection);


  public static class DefaultIntCollectionFactory extends IntCollectionFactory {
    protected WritableIntList _createList(int allocateSize) {
      // todo initialize array with segment size corresponding to allocateSize
      return new SegmentedIntArray();
    }

    protected WritableIntList _createSameValuesList(int allocateSize) {
      // todo - was ListIntMap, which is now removed for SameValuesIntList
      return new SameValuesIntList(new IntIntMap());
    }

    protected ListIntMap _createListIntMap(int allocateSize) {
      return new ListIntMap(_createList(allocateSize));
    }

    protected void _dispose(Object collection) {
      if (collection instanceof WritableIntList) {
        ((WritableIntList) collection).clear();
      } else if (collection instanceof ListIntMap) {
        ((ListIntMap) collection).clear();
      } 
    }
  }
}
