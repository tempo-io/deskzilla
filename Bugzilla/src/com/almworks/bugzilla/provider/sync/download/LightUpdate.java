package com.almworks.bugzilla.provider.sync.download;

import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.provider.sync.*;
import com.almworks.util.commons.Procedure;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

/**
* @author dyoma
*/
class LightUpdate implements Procedure<BugInfo> {
  private final Task myTask;
  private final DBQueue myQueue;
  private BugInfo myBestUpdateTime = null;

  public LightUpdate(Task task) {
    myTask = task;
    myQueue = new DBQueue(task.getContext());
  }

  @Override
  public void invoke(BugInfo bug) {
    BugBox box = updateLightInfo(bug, myTask);
    if (box == null) return;
    if (!shouldStoreToDB(box)) return;
    myQueue.addToUpdate(box);
    synchronized (this) {
      myBestUpdateTime = chooseBestUpdateTime(myBestUpdateTime, bug);
    }
  }

  @SuppressWarnings({"RedundantIfStatement"})
  public static boolean shouldStoreToDB(BugBox box) {
    if (box.getID() == null) return false; // Not uploaded new bug
    if (box.getBugInfo() == null) return false; // Not downloaded
    return true;
  }

  public static BugInfo chooseBestUpdateTime(@Nullable BugInfo best, @NotNull BugInfo bug) {
    Integer issueId = bug.getID();
    String stringMTime = bug.getStringMTime();
    if (issueId == null || stringMTime == null || issueId <= 0 || stringMTime.length() == 0) return best;
    return best == null || bug.getMTime() > best.getMTime() ? bug : best;
  }

  public boolean waitFinished() throws InterruptedException {
    return myQueue.waitFinished();
  }

  public static BugBox updateLightInfo(BugInfo bug, Task task) {
    Integer id = bug.getID();
    BugBox box = task.getSyncData().getBugBox(id);
    if (box == null) {
      Log.warn("bug without box - " + id);
      return null;
    }
    box.setInfoLight(bug);
    BugInfo.ErrorType error = bug.getError();
    box.setError(error);
    long existing = box.getItem();
    if (existing > 0) {
      if (error != null) {
        task.getController().onProblem(
          new InaccessibleItemProblem(existing, id, System.currentTimeMillis(), task.getContext(),
            error, task.getContext().getConfiguration().getValue().getCredentialsInfo()), box);
      } else {
        task.getController().noProblemsForItem(existing, false);
      }
    }
    return box;
  }

  @Nullable
  public BugInfo getBestUpdateTime() {
    synchronized (this) {
      return myBestUpdateTime;
    }
  }

  public long getLastCommitICN() {
    return myQueue.getLastCommitICN();
  }
}
