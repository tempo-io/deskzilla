package com.almworks.bugzilla.provider;

import com.almworks.api.engine.*;
import com.almworks.api.store.Store;
import com.almworks.api.store.StoreUtils;
import com.almworks.spi.provider.util.ServerSyncPoint;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.io.persist.*;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import org.almworks.util.*;
import util.concurrent.SynchronizedBoolean;

import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public class FullSynchroState implements SynchroState {
  private static final String myStoreId = "*";

  private final Persistent myPersistent = new Persistent();
  private final Store myStore;
  private final SynchronizedBoolean myReadFromStore = new SynchronizedBoolean(false);
  private final BasicScalarModel<SyncTask.State> myState = BasicScalarModel.createWithValue(SyncTask.State.NEVER_HAPPENED, true);

  private SyncParameters mySyncParameters = null;
  private boolean myClearStore;
  private ServerSyncPoint mySyncPoint;

  public FullSynchroState(Store store, Boolean isNewProvider) {
    assert store != null;
    myClearStore = isNewProvider.booleanValue();
    myStore = store.getSubStore("sync");
  }

  public synchronized void setSyncState(SyncTask.State state) {
    restoreState();
    myState.setValue(state);
    myPersistent.mySyncState.set(state.getName());
    writeState();
  }

  public ScalarModel<SyncTask.State> getSyncStateModel() {
    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        restoreState();
      }
    });
    return myState;
  }

  public synchronized void update(SyncParameters parameters) {
    if (mySyncParameters == null)
      mySyncParameters = new SyncParameters();
    mySyncParameters = mySyncParameters.merge(parameters);
  }

  public void setSyncPoint(ServerSyncPoint syncPoint) {
    restoreState();
//    writeState();
    ServerSyncPoint point = getSyncPoint();
    if (point == null || !point.isValidSuccessorState(syncPoint)) {
      Log.warn("bad successor sync state: " + point + " => " + syncPoint);
//      assert false : point + " " + syncPoint;
    }
    mySyncPoint = syncPoint;
    ServerSyncPoint.write(syncPoint, myStore);
  }


  public SyncParameters extractParameters() {
    SyncParameters result = mySyncParameters == null ? createDefaultParameters() : mySyncParameters;
    mySyncParameters = null;
    return result;
  }

  public ServerSyncPoint getSyncPoint() {
    restoreState();
    ServerSyncPoint syncPoint = mySyncPoint;
    if (syncPoint == null)
      mySyncPoint = syncPoint = ServerSyncPoint.read(myStore);
    if (syncPoint.isUnsynchronized()) {
      // migrate from DZ 1.5
      long v = myPersistent.mySyncPoint.access().longValue();
      if (v > Const.DAY) {
        myPersistent.mySyncPoint.set(0L);
        writeState();
        mySyncPoint = syncPoint = new ServerSyncPoint(v, 0, null);
      }
    }
    return syncPoint;
  }

  public synchronized Map<Integer, SyncType> extractPendingBugs() {
    restoreState();
    Map<Integer, SyncType> map = myPersistent.myPendingBugs.access();
    if (map.size() == 0)
      return Collections15.emptyMap();
    Map<Integer, SyncType> result = Collections15.hashMap(map);
    map.clear();
    return result;
  }

  private SyncParameters createDefaultParameters() {
    Log.warn("using default sync parameters");
    return SyncParameters.downloadChangesAndMeta();
  }

  private synchronized void restoreState() {
    if (!myReadFromStore.commit(false, true))
      return;
    if (myClearStore) {
      myClearStore = false;
      myStore.access(myStoreId).clear();
      mySyncPoint = ServerSyncPoint.unsynchronized();
      ServerSyncPoint.write(mySyncPoint, myStore);
      return;
    }
    boolean success = StoreUtils.restorePersistable(myStore, myStoreId, myPersistent);
    if (!success) {
      Log.warn("sync state read error, sync state lost");
      myStore.access(myStoreId).clear();
    } else {
      restoreSyncState();
    }
  }

  private void restoreSyncState() {
    SyncTask.State savedState = Util.NN(SyncTask.State.forName(myPersistent.mySyncState.access()),
      SyncTask.State.NEVER_HAPPENED);
    if (savedState == SyncTask.State.SUSPENDED || savedState == SyncTask.State.WORKING) {
      // the application was terminated in the midst of synchronization
      savedState = SyncTask.State.CANCELLED;
    }
    myState.setValue(savedState);
  }

  private synchronized void writeState() {
    if (!myReadFromStore.get())
      throw new IllegalStateException("state was not read");
    StoreUtils.storePersistable(myStore, myStoreId, myPersistent);
  }

  private final class Persistent extends PersistableContainer {
    private final PersistableHashMap<Integer, SyncType> myPendingBugs;
    private final PersistableLong myLastSyncStartTime = new PersistableLong(0);
    private final PersistableString mySyncState = new PersistableString();
    private final PersistableLong mySyncPoint = new PersistableLong(0);

    public Persistent() {
      myPendingBugs = PersistableHashMap.create(new PersistableInteger(),
        PersistableKey.create(SyncType.REGISTRY, SyncType.class));

      persist(myPendingBugs);
      persist(myLastSyncStartTime);
      persist(mySyncState);
      persist(mySyncPoint);
    }
  }
}
