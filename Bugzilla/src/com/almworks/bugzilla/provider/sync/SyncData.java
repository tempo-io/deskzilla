package com.almworks.bugzilla.provider.sync;

import com.almworks.api.engine.SyncParameters;
import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.*;
import com.almworks.items.sync.SyncManager;
import com.almworks.spi.provider.CancelFlag;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.spi.provider.util.ServerSyncPoint;
import com.almworks.util.model.BasicScalarModel;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * This class holds all shared info for a single synchronization process.
 *
 * @author sereda
 */
public class SyncData {
  private final BugzillaContext myContext;
  private final SynchroState mySynchroState;
  private final CancelFlag myCancelFlag;
  private final SyncParameters myParameters;

  /**
   * All bug boxes
   */
  private final List<BugBox> myBugBoxes = Collections15.arrayList();

  /**
   * Bug boxes that have bug ID.
   */
  private final Map<Integer, BugBox> myBugBoxMap = Collections15.hashMap();

  private List<BugBox> myBugBoxesReadOnly = null;

  /**
   * Sync point that should be stored into syncState, may be null.
   */
  private final BasicScalarModel<Long> myLastCommittedWCN;
  private final BugzillaIntegration myIntegration;
  private boolean myUpdateSyncPointLater;
  private ServerSyncPoint myNextSyncPoint;

  public SyncData(BugzillaContext context, SynchroState synchroState, CancelFlag cancelFlag,
    BasicScalarModel<Long> lastCommittedWCN, BugzillaAccessPurpose purpose) throws ConnectionNotConfiguredException {
    assert context != null;
    assert synchroState != null;
    assert cancelFlag != null;
    assert lastCommittedWCN != null;
    assert purpose != null;
    myLastCommittedWCN = lastCommittedWCN;
    myCancelFlag = cancelFlag;
    myContext = context;
    mySynchroState = synchroState;
    myParameters = synchroState.extractParameters();
    myIntegration = context.getIntegration(purpose);
    assert myParameters != null;
  }

  public SynchroState getSyncState() {
    return mySynchroState;
  }

  public synchronized BugBox updateBox(Integer id, SyncType syncType) {
    assert id != null;
    assert syncType != null;
    assert myBugBoxesReadOnly == null : "bug boxes has already been retrieved - how is update possible?";
    BugBox existingBox = myBugBoxMap.get(id);
    if (existingBox != null) {
      existingBox.updateSyncType(id, syncType);
      return existingBox;
    } else {
      BugBox box = new BugBox(id, syncType);
      myBugBoxMap.put(id, box);
      myBugBoxes.add(box);
      return box;
    }
  }

  public synchronized List<BugBox> getBugBoxes() {
    if (myBugBoxesReadOnly == null)
      myBugBoxesReadOnly = Collections.unmodifiableList(myBugBoxes);
    return myBugBoxesReadOnly;
  }

  public List<BugBox> getBugBoxesCopy() {
    List<BugBox> boxes = getBugBoxes();
    if (boxes.isEmpty()) return Collections15.emptyList();
    else return Collections15.arrayList(boxes);
  }

  public SyncParameters getParameters() {
    return myParameters;
  }

  @NotNull
  public BugzillaContext getContext() {
    return myContext;
  }

  public SyncManager getSyncManager() {
    return myContext.getActor(SyncManager.ROLE);
  }

  public CancelFlag getCancelFlag() {
    return myCancelFlag;
  }

  public synchronized BugBox getBugBox(Integer id) {
    return myBugBoxMap.get(id);
  }

  public synchronized void createNewItemBox(long item, SyncType syncType) {
    assert item > 0;
    assert syncType.isUpload() : "syncType == " + syncType;
    BugBox box = new BugBox(item, syncType);
    myBugBoxes.add(box);
  }

  public void logCommit(Long wcn) {
    if (wcn == null)
      return;
    synchronized (myLastCommittedWCN.getLock()) {
      // todo: possible deadlock: setValue() under lock
      Long value = myLastCommittedWCN.getValue();
      if (value == null || value < wcn)
        myLastCommittedWCN.setValue(wcn);
    }
  }

  public BugzillaIntegration getIntegration() {
    return myIntegration;
  }

  public synchronized void setNextSyncPoint(ServerSyncPoint nextSyncPoint) {
    myNextSyncPoint = nextSyncPoint;
  }

  public synchronized ServerSyncPoint getNextSyncPoint() {
    return myNextSyncPoint;
  }

  public synchronized void setUpdateSyncPointLater(boolean update) {
    myUpdateSyncPointLater = update;
  }

  public synchronized boolean isUpdateSyncPointLater() {
    return myUpdateSyncPointLater;
  }

  public synchronized void cleanBugBox(BugBox box) {
    if (box == null)
      return;
    Integer id = box.getID();
    if (id == null) {
      Log.warn("cannot clean new box " + box);
      return;
    }
    Log.debug("cleaning box " + id);
    boolean removed = myBugBoxes.remove(box);
    if (!removed) {
      Log.warn("no box " + box + " in " + this);
    }
    BugBox removedBox = myBugBoxMap.remove(id);
    if (removedBox != box && removedBox != null) {
      Log.warn("box removed from map is different " + box + " vs " + removedBox);
    }
  }
}
