package com.almworks.syncreg;

import com.almworks.api.engine.Connection;
import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.api.syncreg.*;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.events.EventSource;
import com.almworks.util.events.FireEventSupport;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.io.persist.*;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.threads.Bottleneck;
import com.almworks.util.threads.Threads;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;

import java.util.Iterator;
import java.util.Map;

class SyncRegistryImpl implements SyncRegistry, SyncFlagRegistry, Startable, SyncRegistry.Listener {
  private static final String FLAGS_STORE_KEY = "bool";
  private static final String CUBE_STORE_KEY = "cubes";

  private final FireEventSupport<Listener> myEvents = FireEventSupport.createSynchronized(Listener.class);
  /**
   * Map ConnectionID -> ( ID -> FLAG )
   */
  private final Map<String, Map<String, Boolean>> myMap = Collections15.hashMap();
  private final Store myStore;
  private final BasicScalarModel<Boolean> myLoaded = BasicScalarModel.createWithValue(false, true);
  private final SyncCubeRegistryImpl myCubeRegistry = new SyncCubeRegistryImpl(this);

  private Bottleneck mySave;
  private int myUpdateLockers = 0;
  private boolean myMoreWhileLocked = false;
  private boolean myLessWhileLocked = false;
  private final Notifier[] myNotifiers = createNotifiers();

  private Notifier[] createNotifiers() {
    Notifier[] notifiers = new Notifier[4];
    notifiers[getIndex(true, false)] = new Notifier(true, false);
    notifiers[getIndex(false, true)] = new Notifier(false, true);
    notifiers[getIndex(true, true)] = new Notifier(true, true);
    return notifiers;
  }

  public SyncRegistryImpl(Store store) {
    myStore = store;
  }

  public void start() {
    mySave = new Bottleneck(500, ThreadGate.LONG(this), new Runnable() {
      public void run() {
        if (myLoaded.getValue())
          doSave();
      }
    });

    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        doLoad();
        myLoaded.setValue(true);
      }
    });
  }

  public void stop() {

  }

  public EventSource<Listener> getEventSource() {
    return myEvents;
  }

  public SyncFlagRegistry getSyncFlagRegistry() {
    return this;
  }

  public SyncCubeRegistry getSyncCubeRegistry() {
    return myCubeRegistry;
  }

  public void clearRegistryForConnection(@NotNull Connection connection) {
    clearFlags(connection.getConnectionID());

    long connectionItem = connection.getConnectionItem();
    ItemHypercubeImpl cube = new ItemHypercubeImpl();
    cube.addValue(SyncAttributes.CONNECTION, connectionItem, true);
    myCubeRegistry.setUnsynced(cube);
  }

  public void lockUpdate() {
    Threads.assertAWTThread();
    myUpdateLockers++;
    myMoreWhileLocked = false;
    myLessWhileLocked = false;
  }

  public void unlockUpdate() {
    Threads.assertAWTThread();
    if (--myUpdateLockers <= 0) {
      if (myUpdateLockers < 0) {
        assert false : myUpdateLockers;
        myUpdateLockers = 0;
      }
      boolean more = myMoreWhileLocked;
      boolean less = myLessWhileLocked;
      myMoreWhileLocked = false;
      myLessWhileLocked = false;
      if (more || less) {
        myEvents.getDispatcher().onSyncRegistryChanged(more, less);
      }
    }
  }

  public ScalarModel<Boolean> getStartedModel() {
    return myLoaded;
  }

  public boolean isSyncFlag(String connectionID, String id) {
    if (!myLoaded.getValue()) {
      assert false;
      return false;
    }
    if (connectionID == null || id == null || !myLoaded.getValue())
      return false;
    synchronized (myMap) {
      Map<String, Boolean> syncMap = getMapByConnectionID(connectionID);
      Boolean v = syncMap.get(id);
      return v != null && v.booleanValue();
    }
  }

  public void setSyncFlag(String connectionID, String id, boolean flag) {
    if (!myLoaded.getValue()) {
      assert false;
      return;
    }
    if (connectionID == null || id == null || !myLoaded.getValue())
      return;
    boolean changed;
    synchronized (myMap) {
      Map<String, Boolean> syncMap = getMapByConnectionID(connectionID);
      if (flag) {
        Boolean prev = syncMap.put(id, Boolean.TRUE);
        changed = prev == null || !prev.booleanValue();
      } else {
        Boolean prev = syncMap.remove(id);
        changed = prev != null && prev.booleanValue();
      }
    }
    if (changed)
      onSyncRegistryChanged(flag, !flag);
  }

  private void save() {
    if (mySave != null && myLoaded.getValue())
      mySave.requestDelayed();
  }

  public void clearFlags(String connectionID) {
    if (!myLoaded.getValue()) {
      assert false;
      return;
    }
    if (connectionID == null)
      return;
    Map<String, Boolean> removed;
    synchronized (myMap) {
      removed = myMap.remove(connectionID);
    }
    if (removed != null) {
      onSyncRegistryChanged(false, true);
    } else {
      Log.debug("no sync map for connection " + connectionID);
    }
  }

  private void doLoad() {
    Persistable<Map<String, Map<String, Boolean>>> persister = createPersister();
    boolean restored = StoreUtils.restorePersistable(myStore, FLAGS_STORE_KEY, persister);
    if (restored) {
      synchronized (myMap) {
        Map<String, Map<String, Boolean>> map = persister.access();
        if (map != null)
          myMap.putAll(map);
      }
    }
    myCubeRegistry.load(myStore, CUBE_STORE_KEY);
    onSyncRegistryChanged(true, true);
  }

  public void onSyncRegistryChanged(boolean moreSynchronized, boolean lessSynchronized) {
    assert moreSynchronized || lessSynchronized;
    if (!moreSynchronized && !lessSynchronized) {
        return;
    }
    ThreadGate.AWT.execute(getNotifier(moreSynchronized, lessSynchronized));
  }

  private Notifier getNotifier(boolean more, boolean less) {
    return myNotifiers[getIndex(more, less)];
  }

  private static int getIndex(boolean more, boolean less) {
    int index = 0;
    if (more) {
      index += 1;
    }
    if (less) {
      index += 2;
    }
    return index;
  }

  private class Notifier implements Runnable {
    private final boolean myMore;
    private final boolean myLess;

    public Notifier(boolean more, boolean less) {
      myMore = more;
      myLess = less;
    }

    public void run() {
      Threads.assertAWTThread();
      save();
      if (myUpdateLockers == 0) {
        myEvents.getDispatcher().onSyncRegistryChanged(myMore, myLess);
      } else {
        if (myMore)
          myMoreWhileLocked = true;
        if (myLess) {
          myLessWhileLocked = true;
        }
      }
    }
  }

  private static Persistable<Map<String, Map<String, Boolean>>> createPersister() {
    return new PersistableHashMap<String, Map<String, Boolean>>(new PersistableString(),
      new PersistableHashMap<String, Boolean>(new PersistableString(), new PersistableBoolean()));
  }

  private void doSave() {
    if (!myLoaded.getValue())
      return;
    Persistable<Map<String, Map<String, Boolean>>> myPersister = createPersister();
    myPersister.set(deepCopy());
    StoreUtils.storePersistable(myStore, FLAGS_STORE_KEY, myPersister);
    myCubeRegistry.save(myStore, CUBE_STORE_KEY);
  }

  private Map<String, Map<String, Boolean>> deepCopy() {
    synchronized (myMap) {
      Map<String, Map<String, Boolean>> result = Collections15.hashMap();
      for (Iterator<Map.Entry<String, Map<String, Boolean>>> ii = myMap.entrySet().iterator(); ii.hasNext();) {
        Map.Entry<String, Map<String, Boolean>> entry = ii.next();
        result.put(entry.getKey(), Collections15.hashMap(entry.getValue()));
      }
      return result;
    }
  }

  private Map<String, Boolean> getMapByConnectionID(String connectionID) {
    assert Thread.holdsLock(myMap);
    Map<String, Boolean> syncMap = myMap.get(connectionID);
    if (syncMap == null) {
      syncMap = Collections15.hashMap();
      myMap.put(connectionID, syncMap);
    }
    return syncMap;
  }

}
