package com.almworks.bugzilla.provider.sync.upload;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncProblem;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.provider.CommonMetadata;
import com.almworks.bugzilla.provider.datalink.ItemLink;
import com.almworks.bugzilla.provider.datalink.UploadNotPossibleException;
import com.almworks.bugzilla.provider.sync.*;
import com.almworks.integers.LongArray;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.ItemDiffImpl;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.L;
import com.almworks.util.Pair;
import org.almworks.util.Log;

class PrepareUploadBox extends TwoStepsTask implements ItemUploader {
  private final BugInfoForUpload myUpdateInfo = new BugInfoForUpload();
  private final BugBox myBox;

  public PrepareUploadBox(SyncController controller, BugBox box) {
    super(controller, "prepare-upload-" + SyncUtil.getSuffix(box), L.progress("Uploading bug " + SyncUtil.getSuffix(box)), 500, false);
    myBox = box;
    setSecondTask(new FinishUpload(controller, box, myUpdateInfo));
  }

  @Override
  protected void doRun() throws ConnectorException, InterruptedException {
    boolean success = false;
    try {
      UploadProcess process =
        SyncUtils.syncUpload(getSyncData().getSyncManager(), LongArray.create(myBox.getItem()), this);
      if (process == null) return;
      continueUpload(process);
      success = true;
    } finally {
      if (!success) setDone();
    }
  }

  @Override
  public void prepare(UploadPrepare prepare, ItemVersion trunk, boolean uploadAllowed) {
    boolean success = false;
    try {
      checkCancelled();
      ItemVersion server = trunk.switchToServer();
      ItemDiff change;
      if (server == null) {
        prepare.removeFromUpload(trunk.getItem());
        change = ItemDiffImpl.createNoChange(trunk);
      } else change = ItemDiffImpl.create(server, trunk);
      ItemLink link = trunk.mapValue(DBAttribute.TYPE, CommonMetadata.ITEM_LINKS);
      if (link != null) link.buildUploadInfo(prepare, change, myUpdateInfo);
      else prepare.removeFromUpload(trunk.getItem());
      success = true;
    } catch (UploadNotPossibleException e) {
      reportUploadNotPossible(trunk.getItem(), e);
    } catch (CancelledException e) {
      success = false;
    } finally {
      if (!success) prepare.cancelUpload();
    }
  }

  @Override
  public void doUpload(UploadProcess process) {
    Log.error("Should not happen");
  }

  private void continueUpload(UploadProcess process) {
    myBox.uploadAllowed(process);
    myUpdateInfo.uploadAllowed(process);
    try {
      transfer();
    } catch (CancelledException e) {
      process.uploadDone();
    }
  }

  private void reportUploadNotPossible(long item, UploadNotPossibleException e) {
    Pair<String, Boolean> credentials = getContext().getConfiguration().getValue().getCredentialsInfo();
    String cause = e.getCauseString();
    SyncProblem problem = new UpdateImpossibleProblem(item, myBox.getID(), System.currentTimeMillis(), getContext(), cause, credentials);
    myController.onProblem(problem, myBox);
    getSyncData().cleanBugBox(myBox);
  }
}
