 package com.almworks.items.impl.sqlite;

 import com.almworks.integers.*;
 import com.almworks.integers.util.LongListConcatenation;
 import com.almworks.items.impl.DBReaderImpl;
 import com.almworks.items.impl.dbadapter.*;
 import com.almworks.sqlite4java.*;
 import com.almworks.util.Break;
 import com.almworks.util.collections.arrays.ArrayStorageAccessor;
 import com.almworks.util.collections.arrays.NullableArrayStorageAccessor;
 import org.almworks.util.*;
 import org.almworks.util.detach.*;
 import org.jetbrains.annotations.*;

 import java.util.*;
 import java.util.concurrent.atomic.AtomicInteger;

 public class ItemCacheImpl implements ItemCache, ItemSource.Listener {
  private final ItemSource mySource;
  private final Lifecycle myLifecycle = new Lifecycle(false);

  private final PrioritizedListeners<Listener> myListeners = new PrioritizedListeners<Listener>();
  private final AtomicInteger myPriority = new AtomicInteger(Integer.MIN_VALUE);
  
  private final List<SyncValueLoader> myAccessors;
  private final List<Object> myLoadedObjects;
  private final MyLoadSink[] myLoadSinks;
  private final NativeArrayItemAccessor myItemAccessor;

  private final Object myLock = new Object();

  // protected with myLock
  private long[] myItemsSorted;
  private int myItemsCount;
  private final Object[] myData;

  public ItemCacheImpl(ItemSource source, List<? extends SyncValueLoader> accessors) {
    mySource = source;
    int acount = accessors.size();
    assert acount > 0;
    myAccessors = Collections15.unmodifiableListCopy(accessors);
    myData = new Object[acount];
    myLoadedObjects = Collections.unmodifiableList(Arrays.asList(myData));
    myLoadSinks = new MyLoadSink[acount];
    for (int i = 0; i < acount; i++) {
      myLoadSinks[i] = new MyLoadSink(i, accessors.get(i).getArrayAccessor());
    }
    myItemAccessor = new NativeArrayItemAccessor(myAccessors, myLoadedObjects);
  }

  public void start() {
    if (!myLifecycle.cycleStart())
      return;
    mySource.addListener(myLifecycle.lifespan(), myPriority.get(), this);
  }

  public void stop() {
    myLifecycle.cycleEnd();
  }

  public void addListener(Lifespan life, int priority, final Listener listener) {
    if (life.isEnded())
      return;
    myListeners.add(listener, priority);
    life.add(new Detach() {
      protected void doDetach() throws Exception {
        myListeners.remove(listener);
        updatePriority();
      }
    });
    updatePriority();
  }

  private void updatePriority() {
    int priority = myListeners.getTotalPriority();
    int oldPriority = myPriority.getAndSet(priority);
    if (oldPriority != priority) {
      mySource.setPriority(priority, this);
    }
  }

  public void setPriority(int priority, Listener listener) {
    myListeners.setPriority(priority, listener);
    updatePriority();
  }

  public <T> void visit(LongIterable items, Visitor<T> visitor) {
    T visitPayload = null;
    try {
      synchronized (myLock) {
        for (LongIterator ii = items.iterator(); ii.hasNext();) {
          long item = ii.next();
          int p = LongCollections.binarySearch(item, myItemsSorted, 0, myItemsCount);
          myItemAccessor.setPosition(item, p);
          visitPayload = visitor.visitItem(myItemAccessor, visitPayload);
        }
      }
    } catch (Break e) {
      // ignore
    }
  }

  public <T> T visit(long item, Visitor<T> visitor) {
    synchronized (myLock) {
      int p = LongCollections.binarySearch(item, myItemsSorted, 0, myItemsCount);
      myItemAccessor.setPosition(item, p);
      try {
        return visitor.visitItem(myItemAccessor, null);
      } catch (Break aBreak) {
        return null;
      }
    }
  }

  public <T> void visitAll(Visitor<T> visitor) {
    T visitPayload = null;
    try {
      synchronized (myLock) {
        for (int i = 0; i < myItemsCount; i++) {
          myItemAccessor.setPosition(myItemsSorted[i], i);
          visitPayload = visitor.visitItem(myItemAccessor, visitPayload);
        }
      }
    } catch (Break e) {
      // ignore
    }
  }

  public void reload(TransactionContext context, String idTableName) throws SQLiteException {
    WritableLongList ids = LongCollectionFactory.createList();
    fullLoad(context, idTableName, ids);
    Iterator<Listener> ii;
    synchronized (myLock) {
      clear();
      processUpdate(context, LongList.EMPTY, ids, LongList.EMPTY);
      ii = myListeners.iterator();
    }
    while (ii.hasNext())
      ii.next().onReload(this);
  }

  private void fullLoad(TransactionContext context, String idTableName, WritableLongList ids) throws SQLiteException {
    SQLiteStatement stmt = context.prepare(new SQLParts().append("SELECT id FROM ").append(idTableName));
    try {
      SQLUtil.loadLongs(stmt, context, ids);
    } finally {
      stmt.dispose();
    }
  }

  private void clear() {
    assert Thread.holdsLock(myLock);
    myItemsCount = 0;
  }

  public void update(TransactionContext context, ItemSourceUpdateEvent event, String idTableName, boolean forced) throws SQLiteException {
    if (event.isEmpty())
      return;
    LongList removed = event.getRemovedItemsSorted();
    LongList added = event.getAddedItemsSorted();
    LongList changed = event.getUpdatedItemsSorted();
    Iterator<Listener> ii;
    synchronized (myLock) {
      if (changed == null) {
        // global change event
        changed = new LongArray(ArrayUtil.arrayCopy(myItemsSorted, 0, myItemsCount));
      }
      processUpdate(context, removed, added, changed);
      ii = myListeners.iterator();
    }
    while (ii.hasNext())
      ii.next().onUpdate(this, added, removed, changed);
  }

  private void processUpdate(TransactionContext context, @NotNull LongList removedSorted,
    @NotNull LongList addedSorted, @NotNull LongList changedSorted) throws SQLiteException
  {
    assert Thread.holdsLock(myLock);
    if (!removedSorted.isEmpty()) {
      removeData(removedSorted);
    }

    // todo IntIteratorIterator from IntList.Concatenation
    LongList loadingItems = LongListConcatenation.concatUnmodifiable(addedSorted, changedSorted);
    if (!loadingItems.isEmpty()) {
      DBReaderImpl access = new DBReaderImpl(context);
      try {
        for (int li = 0; li < myAccessors.size(); li++) {
          SyncValueLoader loader = myAccessors.get(li);
          loader.load(access, loadingItems.iterator(), myLoadSinks[li]);
        }
      } catch (Break e) {
        assert false : e;
        Log.warn(e);
      }
    }
  }

  private void removeData(LongList removed) {
    assert Thread.holdsLock(myLock);
    for (LongListIterator ii = removed.iterator(); ii.hasNext();) {
      long item = ii.next();
      int idx = LongCollections.binarySearch(item, myItemsSorted, 0, myItemsCount);
      if (idx >= 0) {
        if (idx < myItemsCount)
          System.arraycopy(myItemsSorted, idx + 1, myItemsSorted, idx, myItemsCount - idx);
        myItemsCount--;
        for (int li = 0; li < myAccessors.size(); li++) {
          ArrayStorageAccessor accessor = myAccessors.get(li).getArrayAccessor();
          myData[li] = accessor.shiftLeft(myData[li], idx, 1);
        }
      }
    }
  }

  private class MyLoadSink implements SyncValueLoader.Sink {
    private final int myIndex;
    private final NullableArrayStorageAccessor myAccessor;

    public MyLoadSink(int index, NullableArrayStorageAccessor accessor) {
      myIndex = index;
      myAccessor = accessor;
    }

    public void onLoaded(LongList requestedItems, LongList loadedItems, Object data) throws Break {
      assert requestedItems.isUniqueSorted();
      assert loadedItems.isUniqueSorted(); 
      int targetIndex = 0;
      int requestedCount = requestedItems.size();
      int loadedCount = loadedItems.size();
      int loadedIndex = 0;
      long loadedItem = Integer.MIN_VALUE;

      for (int i = 0; i < requestedCount; i++) {
        long item = requestedItems.get(i);
        while (loadedIndex < loadedCount && loadedItem < item) {
          loadedItem = loadedItems.get(loadedIndex++);
        }
        if (loadedItem == item) {
          targetIndex = setDataIncremental(myIndex, targetIndex, item, myAccessor, data, loadedIndex - 1);
        } else {
          targetIndex = setDataIncremental(myIndex, targetIndex, item, myAccessor, null, -1);
        }
      }
    }

    private int setDataIncremental(int aIndex, int dIndex, long item, NullableArrayStorageAccessor accessor, Object data,
      int dataIndex)
    {
      int idx = LongCollections.binarySearch(item, myItemsSorted, dIndex, myItemsCount);
      if (idx < 0) {
        idx = -idx - 1;
        myItemsSorted = ArrayUtil.ensureCapacity(myItemsSorted, myItemsCount + 1);
        if (myItemsCount > idx) {
          System.arraycopy(myItemsSorted, idx, myItemsSorted, idx + 1, myItemsCount - idx);
        }
        myItemsSorted[idx] = item;
        myItemsCount++;
      }

      if (data == null) {
        myData[aIndex] = accessor.setNull(myData[aIndex], idx);
      } else {
        myData[aIndex] = accessor.copyValue(data, dataIndex, myData[aIndex], idx);
      }
      return idx;
    }
  }
}
