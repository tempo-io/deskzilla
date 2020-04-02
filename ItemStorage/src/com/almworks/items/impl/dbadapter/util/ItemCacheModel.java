package com.almworks.items.impl.dbadapter.util;

import com.almworks.integers.LongList;
import com.almworks.integers.LongListIterator;
import com.almworks.integers.util.LongListConcatenation;
import com.almworks.items.impl.dbadapter.ItemAccessor;
import com.almworks.items.impl.dbadapter.ItemCache;
import com.almworks.util.Break;
import com.almworks.util.advmodel.AListModel;
import com.almworks.util.advmodel.AListModelUpdater;
import com.almworks.util.threads.ThreadSafe;
import org.almworks.util.Collections15;
import org.almworks.util.detach.Lifecycle;
import org.jetbrains.annotations.*;

import java.util.Map;
import java.util.Set;

public abstract class ItemCacheModel<T> {
  private static final int MODEL_PRIORITY = 1000;

  private final Lifecycle myLifecycle = new Lifecycle(false);
  private final ItemCache myCache;

  private final AListModelUpdater<T> myModel = AListModelUpdater.create(100);

  // todo effective map
  /**
   * myMap is usually accessed from db thread, but it may be accessed from awt thread also, at startup
   * thus it's protected with synchronized(myMap)
   */
  private final Map<Long, T> myMap = Collections15.hashMap();

  private final ItemCache.Listener myCacheListener = new ItemCache.Listener() {
    public void onReload(ItemCache source) {
      assert source == myCache;
      reload();
    }

    public void onUpdate(ItemCache source, LongList added, LongList removed, LongList changed) {
      assert source == myCache;
      update(added, removed, changed);
    }
  };

  private final MyVisitor myVisitor = new MyVisitor();

  public ItemCacheModel(ItemCache cache) {
    myCache = cache;
  }

  /**
   * Implement this method to create elements of type T. The method is called each time underlying cache is updated.
   *
   * @param itemAccessor
   * @param oldElement element that has previously been created for this item, or null @return null if an element for this item should not be created, or should be removed from the model; an element
   */
  @Nullable
  protected abstract T createOrUpdateElement(ItemAccessor itemAccessor, @Nullable T oldElement);

  private void update(LongList added, LongList removed, LongList changed) {
    synchronized (myMap) {
      if (!removed.isEmpty()) {
        for (LongListIterator ii = removed.iterator(); ii.hasNext();) {
          T elem = myMap.remove(ii.next());
          if (elem != null) {
            myModel.remove(elem);
          }
        }
      }
      LongList updating = LongListConcatenation.concatUnmodifiable(added, changed);
      if (!updating.isEmpty()) {
        myCache.visit(updating, myVisitor);
      }
    }
  }

  @ThreadSafe
  private void reload() {
    synchronized (myMap) {
      myVisitor.initializeRemaining();
      myCache.visitAll(myVisitor);
      Set<Long> remaining = myVisitor.collectRemaining();
      if (!remaining.isEmpty()) {
        for (Long item : remaining) {
          T value = myMap.remove(item);
          if (value != null) {
            myModel.remove(value);
          }
        }
      }
    }
  }

  public AListModel<T> getModel() {
    return myModel.getModel();
  }

  public void start() {
    if (!myLifecycle.cycleStart())
      return;
    myCache.start();
    myCache.addListener(myLifecycle.lifespan(), MODEL_PRIORITY, myCacheListener);
    reload();
  }

  public void stop() {
    myLifecycle.cycleEnd();
  }

  public ItemCache getCache() {
    return myCache;
  }

  public T getCachedItem(int item) {
    synchronized (myMap) {
      return myMap.get(item);
    }
  }

  public void waitFlush() throws InterruptedException {
    assert false : "reimport";
//    myModel.waitFlush();
  }

  private class MyVisitor implements ItemCache.Visitor<Object> {
    // todo effective class
    private Set<Long> myRemaining;

    public void initializeRemaining() {
      assert Thread.holdsLock(myMap);
      myRemaining = Collections15.hashSet(myMap.keySet());
    }

    public Set<Long> collectRemaining() {
      assert Thread.holdsLock(myMap);
      Set<Long> r = myRemaining;
      myRemaining = null;
      return r;
    }

    public Object visitItem(ItemAccessor itemAccessor, Object visitPayload) throws Break
    {
      assert Thread.holdsLock(myMap);
      long item = itemAccessor.getItem();
      T eold = myMap.get(item);
      if (myRemaining != null) {
        myRemaining.remove(item);
      }
      T enew = itemAccessor.hasValues() ? createOrUpdateElement(itemAccessor, eold) : null;
      if (enew == eold) {
        if (enew != null) {
          AListModelUpdater<T> m = myModel;
          updateElement(enew, m);
        }
      } else {
        if (eold == null) {
          myMap.put(item, enew);
          addElement(enew, myModel);
        } else if (enew == null) {
          myMap.remove(item);
          removeElement(eold, myModel);
        } else {
          assert enew != null && eold != null;
          T expunged = myMap.put(item, enew);
          assert expunged == eold : eold + " " + expunged;
          if (eold.equals(enew))
            replaceElement(eold, enew, myModel);
          else {
            removeElement(eold, myModel);
            addElement(enew, myModel);
          }
        }
      }
      return null;
    }
  }

  private void replaceElement(T eold, T enew, AListModelUpdater<T> updater) {
    assert false : "reimport";
//    updater.replace(eold, enew);
    updater.remove(eold);
    updater.add(enew);
  }

  protected void removeElement(T element, AListModelUpdater<T> updater) {
    updater.remove(element);
  }

  protected void addElement(T element, AListModelUpdater<T> updater) {
    updater.add(element);
  }

  protected void updateElement(T element, AListModelUpdater<T> updater) {
    updater.updateElement(element);
  }
}
