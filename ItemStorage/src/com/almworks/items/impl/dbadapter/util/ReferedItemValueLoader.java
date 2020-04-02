package com.almworks.items.impl.dbadapter.util;

import com.almworks.integers.*;
import com.almworks.items.api.DBReader;
import com.almworks.items.impl.dbadapter.SyncValueLoader;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.util.Break;
import com.almworks.util.collections.arrays.IntArrayAccessor;
import com.almworks.util.collections.arrays.NullableArrayStorageAccessor;
import org.almworks.util.ArrayUtil;

public class ReferedItemValueLoader implements SyncValueLoader {
  private final SyncValueLoader myReferenceLoader;
  private final SyncValueLoader myReferedLoader;

  public ReferedItemValueLoader(SyncValueLoader referenceLoader, SyncValueLoader referedLoader) {
    assert referenceLoader.getArrayAccessor() instanceof IntArrayAccessor : referenceLoader;
    myReferenceLoader = referenceLoader;
    myReferedLoader = referedLoader;
  }

  public NullableArrayStorageAccessor getArrayAccessor() {
    return myReferedLoader.getArrayAccessor();
  }

  public void load(final DBReader reader, LongIterator items, final Sink sink) throws SQLiteException, Break {
    myReferenceLoader.load(reader, items, new Sink() {
      public void onLoaded(LongList requestedItems, LongList loadedItems, Object references)
      throws Break, SQLiteException
      {
        fireNullsLoaded(requestedItems, loadedItems, references, sink);
        loadValues(reader, loadedItems, references, sink);
      }
    });
  }

  private void loadValues(DBReader access, LongList loadedItems, Object references, final Sink sink) throws
    SQLiteException, Break {
    assert loadedItems.isUniqueSorted();
    if (loadedItems.isEmpty()) return;
    IntArrayAccessor refAccessor = getReferenceAccessor();
    final LongArray itemsLeft = new LongArray();
    final LongArray itemRefValues = new LongArray();
    for (int i = 0; i < loadedItems.size(); i++) {
      int ref = refAccessor.getIntValue(references, i);
      if (ref >= 0) {
        itemsLeft.add(loadedItems.get(i));
        itemRefValues.add(ref);
      }
    }
    if (itemsLeft.isEmpty()) {
      assert itemRefValues.isEmpty();
      return;
    }
    itemRefValues.sort(itemsLeft);
    long[] toLoad = itemRefValues.toNativeArray();
    int toLoadCount = ArrayUtil.removeSubsequentDuplicates(toLoad, 0, toLoad.length);
    myReferedLoader.load(access, new LongArrayIterator(toLoad, 0, toLoadCount), new Sink() {
      public void onLoaded(LongList reqReferences, LongList loaderReferences, Object refValues)
          throws Break, SQLiteException
      {
        assert reqReferences.isUniqueSorted();
        assert loaderReferences.isUniqueSorted();
        Object valueStorage = null;
        LongArray items = new LongArray();
        NullableArrayStorageAccessor valueAccessor = myReferedLoader.getArrayAccessor();
        for (int i = 0; i < reqReferences.size(); i++) {
          long ref = reqReferences.get(i);
          int valueIndex = loaderReferences.binarySearch(ref);
          int itemIndex = itemRefValues.binarySearch(ref);
          assert itemIndex == 0 || itemRefValues.get(itemIndex - 1) < ref;
          while (itemRefValues.get(itemIndex) == ref) {
            if (valueIndex < 0)
              valueStorage = valueAccessor.setNull(valueStorage, items.size());
            else
              valueStorage = valueAccessor.copyValue(refValues, valueIndex, valueStorage, items.size());
            items.add(itemsLeft.get(itemIndex));
            itemIndex++;
          }
        }
        items.sortUnique();
        sink.onLoaded(items, items, valueStorage);
      }
    });
  }

  private IntArrayAccessor getReferenceAccessor() {
    return (IntArrayAccessor) myReferenceLoader.getArrayAccessor();
  }

  private void fireNullsLoaded(LongList requestedItems, LongList loadedItems, Object references,
    Sink sink)
    throws Break, SQLiteException
  {
    assert requestedItems.isUniqueSorted();
    assert loadedItems.isUniqueSorted();
    IntArrayAccessor accessor = getReferenceAccessor();
    LongArray nullItems = new LongArray();
    for (int i = 0; i < requestedItems.size(); i++) {
      long item = requestedItems.get(i);
      if (!loadedItems.contains(item) || accessor.getIntValue(references, i) < 0)
        nullItems.add(item);
    }
    assert nullItems.isUniqueSorted();
    if (requestedItems.size() > loadedItems.size()) {
      sink.onLoaded(nullItems, LongList.EMPTY, null);
    }
  }
}
