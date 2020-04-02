package com.almworks.bugzilla.provider.sync.upload;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.integration.err.MidAirCollisionException;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.DataLink;
import com.almworks.bugzilla.provider.datalink.ItemLink;
import com.almworks.bugzilla.provider.sync.*;
import com.almworks.items.api.DBOperationCancelledException;
import com.almworks.items.api.DBResult;
import com.almworks.items.sync.*;
import com.almworks.recentitems.RecentItemsService;
import com.almworks.recentitems.RecordType;
import com.almworks.util.L;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.Collection;

class FinishUpload extends LinearProgressTask {
  private final BugInfoForUpload myUpdateInfo;
  private final BugBox myBox;

  public FinishUpload(SyncController controller, BugBox box, BugInfoForUpload updateInfo) {
    super(controller, "update-" + SyncUtil.getSuffix(box),
      L.progress("Updating bug " + SyncUtil.getSuffix(box)), 1000, true);
    myBox = box;
    myUpdateInfo = updateInfo;
  }

  @Override
  protected void doRun() throws ConnectorException {
    try {
      boolean newBug = myUpdateInfo.getAnyValue(BugzillaAttribute.ID, null) == null;
      boolean success = newBug ? submitNew() : uploadChanged();
      if (!success) return;
      boolean hasExtra = myBox.getFrontPageData() != null;
      if (!hasExtra) SyncUtil.runExtraDownload(myBox, this);
      if (hasExtra) myBox.getBugInfo().updateWith(myBox.getFrontPageData());
//    if (myBox.isSyncAfterUpload() && !myBox.isProblematic());
      ItemUploader.UploadProcess process = myBox.getUploadProcess();
      if (process == null) {
        Log.error("Not allowed upload " + myBox);
        return;
      }
      process.writeUploadState(new DownloadProcedure<UploadDrain>() {
        @Override
        public void write(UploadDrain drain) throws DBOperationCancelledException {
          for (ItemLink link : CommonMetadata.ITEM_LINKS.values()) link.checkUpload(drain, myBox);
          SyncUtil.updateDB(myBox, drain.changeItem(myBox.getItem()), getContext());
        }

        @Override
        public void onFinished(DBResult<?> result) {
          getSyncData().logCommit(result.getCommitIcn());
        }
      }).waitForCompletion();
      cleanBoxProblem();
      getSyncData().cleanBugBox(myBox);
      RecentItemsService ris = getContext().getActor(RecentItemsService.ROLE);
      if(ris != null) {
        ris.addRecord(myBox.getItem(), newBug ? RecordType.NEW_UPLOAD : RecordType.EDIT_UPLOAD);
      }
    } finally {
      myBox.getUploadProcess().uploadDone();
    }
  }

  private void cleanBoxProblem() {
    if (myBox.getSyncType() == SyncType.RECEIVE_AND_SEND) {
      long item = myBox.getItem();
      if (item > 0) {
        if (!myBox.isProblematic()) {
          myController.noProblemsForItem(item, true);
        }
      }
    }
  }

  private boolean submitNew() {
    try {
      myBox.incrementUploadCount();
      BugSubmitResult submitResult = getIntegration().submitBug(myUpdateInfo);
      ConnectorException exception = submitResult.getException();
      Integer id = submitResult.getID();
      if (exception != null) {
        myController.onProblem(new SubmitFailedProblem(myBox.getItem(), id, System.currentTimeMillis(), exception, getContext(),
          getContext().getConfiguration().getValue().getCredentialsInfo()), myBox);
      }
      if (id == null) return false;
      myBox.setID(id);
      reload(false);
      return true;
    } catch (ConnectorException e) {
      myController.onProblem(new BugzillaExceptionProblem(myBox.getItem(), myBox.getID(), System.currentTimeMillis(), getContext(), e,
        getContext().getConfiguration().getValue().getCredentialsInfo()), myBox);
      return false;
    }
  }

  private boolean uploadChanged() throws CancelledException {
    try {
      myBox.incrementUploadCount();
      getIntegration().updateBug(myUpdateInfo);
      checkCancelled();
      reload(true);
      myController.noProblemsForItem(myBox.getItem(), true);
      return true;
    } catch (MidAirCollisionException e) {
      // requires reload
      Log.debug("mid-air collision, reloading bug");
      myController.noProblemsForItem(myBox.getItem(), true);
      return false;
    } catch (ConnectorException e) {
      myController.onProblem(new BugzillaExceptionProblem(myBox.getItem(), myBox.getID(), System.currentTimeMillis(),
        getContext(), e, getContext().getConfiguration().getValue().getCredentialsInfo()), myBox);
      return false;
    }
  }

  private void reload(boolean detectFailedUpdate) throws ConnectorException {
    myBox.clearDownloadedInfo();
    Integer id = myBox.getID();
    assert id != null : myBox;
    BugInfo info = loadSingleBugInfo(id);
    if (info == null) return;
    myBox.setInfoLight(info);
    SyncUtil.runExtraDownload(myBox, info, this);

    if (info.getError() != null) {
      myBox.setError(info.getError());
      myController.onProblem(new InaccessibleItemProblem(myBox.getItem(), id, System.currentTimeMillis(), getContext(),
        info.getError(), getContext().getConfiguration().getValue().getCredentialsInfo()),
        myBox);
      Log.warn(this + ": error reloading bug: " + info);
      return;
    } else {
      myController.noProblemsForItem(myBox.getItem(), false);
    }

    if (detectFailedUpdate) {
      String failedAttribute = detectFailedUpdate(info);
      if (failedAttribute != null) {
        myController.onProblem(new UpdateFailedProblem(myBox.getItem(), id, System.currentTimeMillis(), getContext(),
          failedAttribute, getContext().getConfiguration().getValue().getCredentialsInfo()),
          myBox);
      }
    }
  }

  @Nullable
  private BugInfo loadSingleBugInfo(Integer id) throws ConnectorException {
    // Observed: Bugzilla returns the same bug several times.
    Collection<BugInfo> infos = getIntegration().loadBugDetails(new Integer[] {id}, null);
    if (infos.size() == 1) return infos.iterator().next();
    Log.warn(this + ": weird result size " + infos.size());
    if (infos.isEmpty()) return null;
    String strId = String.valueOf(id);
    boolean found = false;
    for (BugInfo info : infos) {
      String thisId = info.getStringID();
      if (strId.equals(thisId)) found = true;
      else Log.warn("Wrong bug id returned: " + thisId);
    }
    if (!found) {
      Log.warn("Bug id not found: " + strId);
      return null;
    }
    return infos.iterator().next();
  }

  private String detectFailedUpdate(BugInfo info) {
    StringBuffer buffer = new StringBuffer();
    for (DataLink link : CommonMetadata.ATTRIBUTE_LINKS) {
      String failed = link.detectFailedUpdate(info, myUpdateInfo, getPrivateMetadata());
      if (failed != null) {
        if (buffer.length() > 0)
          buffer.append(", ");
        buffer.append(failed);
      }
    }
    return buffer.length() > 0 ? buffer.toString() : null;
  }
}
