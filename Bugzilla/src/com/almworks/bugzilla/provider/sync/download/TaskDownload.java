package com.almworks.bugzilla.provider.sync.download;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncParameter;
import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.FrontPageData;
import com.almworks.bugzilla.provider.sync.*;
import com.almworks.integers.IntArray;
import com.almworks.integers.IntList;
import com.almworks.spi.provider.util.ServerSyncPoint;
import com.almworks.util.L;
import com.almworks.util.progress.Progress;
import org.almworks.util.Const;
import org.almworks.util.Log;

import java.util.Collection;
import java.util.Date;

/**
 * @author dyoma
 */
public class TaskDownload extends LinearProgressTask {
  public TaskDownload(SyncController controller) {
    super(controller, "download-bugs", getDisplayName(controller), 10000, true);
  }

  private static String getDisplayName(SyncController controller) {
    return controller.getData().getParameters().get(SyncParameter.INITIALIZE_CONNECTION) == null
      ? L.progress("Downloading bugs")
      : L.progress("Downloading sample bugs");
  }

  @Override
  protected void doRun() throws ConnectorException, InterruptedException {
    progress(0);
    SyncData data = getSyncData();
    Progress download = myProgress.createDelegate(0.95);
    Progress completion = myProgress.createDelegate(0.05);

    IntList receiveOnly = getBugIds(true);
    double fastPart;
    BugInfo bestUpdateTime;
    boolean writeDone;
    if (!receiveOnly.isEmpty()) {
      LightUpdate updater = new LightUpdate(this);
      fastPart = ((double) receiveOnly.size()) / data.getBugBoxes().size();
      fastPart = fastPart* fastPart;
      Progress fastProgress = download.createDelegate(fastPart);
      getIntegration().loadBugDetails(receiveOnly.toList().toArray(new Integer[receiveOnly.size()]), fastProgress, updater);
      writeDone = updater.waitFinished();
      bestUpdateTime = updater.getBestUpdateTime();
      data.logCommit(updater.getLastCommitICN());
      for (BugBox box : data.getBugBoxesCopy()) {
        Integer id = box.getID();
        if (id != null && LightUpdate.shouldStoreToDB(box) && receiveOnly.contains(id)) {
          assert box.getSyncType() == SyncType.RECEIVE_ONLY;
          data.cleanBugBox(box);
        }
      }
    } else {
      fastPart = 0;
      bestUpdateTime = null;
      writeDone = true;
    }

    IntList receiveFull = getBugIds(false);
    if (!receiveFull.isEmpty()) {
      Progress fullProgress = download.createDelegate(1 - fastPart);
      Collection<BugInfo> infos =
        getIntegration().loadBugDetails(receiveFull.toList().toArray(new Integer[receiveFull.size()]), fullProgress);
      DBQueue queue = new DBQueue(getContext());
      for (BugInfo info : infos) {
        BugBox box = LightUpdate.updateLightInfo(info, this);
        if (box == null || box.getError() != null) continue;
        if (box.getID() != null && box.getSyncType() != SyncType.RECEIVE_ONLY) SyncUtil.runExtraDownload(box, this);
        FrontPageData fpd = box.getFrontPageData();
        if (fpd != null) box.getBugInfo().updateWith(fpd);
        bestUpdateTime = LightUpdate.chooseBestUpdateTime(bestUpdateTime, info);
        queue.addToUpdate(box);
      }
      if (!queue.waitFinished()) writeDone = false;
      data.logCommit(queue.getLastCommitICN());
    }
    if (!writeDone) data.setUpdateSyncPointLater(false);
    download.setDone();

    if (data.isUpdateSyncPointLater() && bestUpdateTime != null) {
      data.setUpdateSyncPointLater(false);
      long mtime = bestUpdateTime.getMTime();
      long now = System.currentTimeMillis();
      if (mtime > now) {
        Log.warn("Latest mtime in future: seems like time zone data is outdated on the Bugzilla server " + new Date(mtime) + ", setting to " + new Date(now));
        mtime = now;
      }
      if (mtime > Const.DAY)
        data.getSyncState().setSyncPoint(new ServerSyncPoint(mtime, bestUpdateTime.getID(), bestUpdateTime.getStringMTime()));
    }
    completion.setDone();
  }

  IntList getBugIds(boolean recieveOnly) {
    IntArray ids = new IntArray();
    for (BugBox box : getSyncData().getBugBoxes()) {
      Integer id = box.getID();
      if (id == null || box.getBugInfo() != null || box.getError() != null) continue;
      if (recieveOnly == (box.getSyncType() == SyncType.RECEIVE_ONLY)) ids.add(id);
    }
    ids.sortUnique();
    return ids;
  }
}
