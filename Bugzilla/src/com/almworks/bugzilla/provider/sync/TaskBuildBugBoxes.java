package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.engine.*;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.integers.LongArray;
import com.almworks.items.api.*;
import com.almworks.items.sync.ItemVersion;
import com.almworks.items.sync.SyncState;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.L;
import org.almworks.util.Log;

import java.util.Map;

/**
 * This task creates BugBoxes from stored sync info and sync parameters.
 *
 * @author sereda
 */
class TaskBuildBugBoxes extends LinearProgressTask {
  public TaskBuildBugBoxes(SyncController controller) {
    super(controller, "build-boxes", L.progress("Starting synchronization"), 500, true);
  }

  public void doRun() throws CancelledException, InterruptedException {
    addPendingBugs();
    progress(0.3F);
    detectBugsRequestedForSync();
    progress(0.9F);
  }

  void addPendingBugs() {
    Map<Integer, SyncType> map = getSyncData().getSyncState().extractPendingBugs();
    if (map == null)
      return;
    for (Map.Entry<Integer, SyncType> entry : map.entrySet()) {
      getSyncData().updateBox(entry.getKey(), entry.getValue());
    }
  }

  void detectBugsRequestedForSync() throws CancelledException, InterruptedException {
    getContext().getActor(Database.ROLE).readBackground(new ReadTransaction<Object>() {
      @Override
      public Object transaction(DBReader reader) throws DBOperationCancelledException {
        try {
          if (addLocallyChanged()) collectLocallyChanged(reader);
          progress(0.6F);
          collectBugsRequestedForSync(reader);
          return null;
        } catch (CancelledException e) {
          throw new DBOperationCancelledException();
        }
      }
    }).waitForCompletion();
  }

  private boolean addLocallyChanged() {
    SyncType syncType = getSyncParameter(SyncParameter.CHANGED_ITEMS);
    if (syncType == null) {
      // synchronization of all changed items was not requested
      syncType = getSyncParameter(SyncParameter.ALL_ITEMS);
      if (syncType != SyncType.RECEIVE_AND_SEND)
        return false;
      // fall through - synchronization of all bugs for send & receive was requested
    }
    return true;
  }

  private void collectLocallyChanged(DBReader reader) throws CancelledException {
    DBFilter bugsView = getContext().getBugsView();
    DBFilter uploadable = bugsView.filter(Bug.STRUCTURE.getUploadableItemsFilter());
    LongArray changes = uploadable.query(reader).copyItemsSorted();
    for (ItemVersion bug : SyncUtils.readItems(reader, changes)) {
      checkCancelled();
      buildBox(bug, SyncType.RECEIVE_AND_SEND);
    }
  }


  private BugBox buildBox(Integer id, SyncType syncType) {
    return getSyncData().updateBox(id, syncType);
  }

  private void buildBox(ItemVersion bug, SyncType syncType) {
    Integer id = bug.getValue(Bug.attrBugID);
    if (id != null) {
      getSyncData().updateBox(id, syncType).setItem(bug.getItem());
    }
    else {
      // new item here, id = null
      if (!syncType.isUpload()) {
        // nothing to download
        return;
      }
      SyncState state = bug.getSyncState();
      if (state != SyncState.NEW) {
        Log.warn(this + ": item " + bug + " is without ID and not new " + state);
        return;
      }
      // ok, a normal new item
      getSyncData().createNewItemBox(bug.getItem(), syncType);
    }
  }

  private void collectBugsRequestedForSync(DBReader reader) {
    Map<Long, SyncType> requested = getSyncParameter(SyncParameter.EXACT_ITEMS);
    if (requested != null)
      for (Map.Entry<Long, SyncType> entry : requested.entrySet())
        buildBox(SyncUtils.readTrunk(reader, entry.getKey()), entry.getValue());

    Map<Integer, SyncType> requestedIds = getSyncParameter(SyncParameter.EXACT_ITEMS_BY_ID);
    if (requestedIds != null) {
      for (Map.Entry<Integer, SyncType> entry : requestedIds.entrySet()) {
        BugBox box = buildBox(entry.getKey(), entry.getValue());
        long bug = Bug.createBugProxy(getPrivateMetadata().thisConnectionItem(), entry.getKey()).findItem(reader);
        if (bug > 0) box.setItem(bug); 
      }
    }
  }

  public boolean isApplicable(SyncParameters syncParameters) {
    // skip this step when initializing connection
    return syncParameters.get(SyncParameter.INITIALIZE_CONNECTION) == null;
  }
}
