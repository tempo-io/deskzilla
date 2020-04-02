package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.SyncParameters;
import com.almworks.api.engine.SyncType;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.L;
import com.almworks.util.progress.Progress;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.Set;

public class UploadBugTask extends BugzillaSyncTask {
  private final Set<Long> myBugsToUpload;
  private final Runnable myAfterSync;

  public UploadBugTask(BugzillaContext context, ComponentContainer subcontainer, Set<Long> bugsToUpload, Runnable afterSync) {
    super(context, subcontainer);
    myBugsToUpload = bugsToUpload;
    myAfterSync = afterSync;
  }

  public String getTaskName() {
    return L.progress("Uploading changes");
  }

  protected void executeTask() {
    try {
      SyncParameters syncParameters = buildSyncParameters(myBugsToUpload);
      boolean success = doSync(syncParameters);
      if (success) {
        myAfterSync.run();
      }
    } finally {
      detach();
    }
  }

  private SyncParameters buildSyncParameters(Set<Long> items) {
    return items.isEmpty() ? null : SyncParameters.syncItems(items, SyncType.RECEIVE_AND_SEND);
  }

  private boolean doSync(SyncParameters syncParameters) {
    boolean success = false;
    if (syncParameters == null || syncParameters.isEmpty()) {
      success = true;
    } else {
      SynchroState state = new ItemsOnlySynchroState(syncParameters, myState);
      try {
        runSynchronization(BugzillaAccessPurpose.UPLOAD_QUEUE, state, new Progress());
        success = true;
      } catch (ConnectorException e) {
        Log.debug(this + ": " + e);
      } catch (ConnectionNotConfiguredException e) {
        // connection is being closed?
        // ignore
      }
    }
    return success;
  }

  @Override
  public SpecificItemActivity getSpecificActivityForItem(long itemId, @Nullable Integer serverId) {
    return myBugsToUpload.contains(itemId) ? SpecificItemActivity.UPLOAD : SpecificItemActivity.OTHER;
  }
}
