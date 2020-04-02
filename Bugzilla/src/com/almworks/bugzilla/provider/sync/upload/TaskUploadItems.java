package com.almworks.bugzilla.provider.sync.upload;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncParameters;
import com.almworks.api.engine.SyncType;
import com.almworks.bugzilla.provider.sync.*;
import com.almworks.util.L;
import com.almworks.util.progress.Progress;
import com.almworks.util.progress.ProgressSource;
import org.almworks.util.Collections15;

import java.util.Iterator;
import java.util.List;

public class TaskUploadItems extends Task {
  protected final Progress myProgress;

  public TaskUploadItems(SyncController controller) {
    super(controller, "upload", L.progress("Upload changed and new bugs"), 10000);
    myProgress = new Progress("upload");
  }

  @Override
  protected void doRun() throws ConnectorException, InterruptedException {
    List<BugBox> upload = Collections15.arrayList(getSyncData().getBugBoxes());
    for (Iterator<BugBox> it = upload.iterator(); it.hasNext();) {
      BugBox box = it.next();
      if (box.getSyncType() != SyncType.RECEIVE_AND_SEND || box.isProblematic()) {
        it.remove();
      }
    }
    if (upload.isEmpty()) {
      myProgress.setDone();
      return;
    }
    float span = 1F / upload.size();
    for (BugBox box : upload) {
      PrepareUploadBox task = new PrepareUploadBox(myController, box);
      myProgress.delegate(task.getProgress(), span);
      myController.runTask(task);
    }
  }

  public boolean isApplicable(SyncParameters syncParameters) {
    return syncParameters.hasSyncType(SyncType.RECEIVE_AND_SEND);
  }

  public ProgressSource getProgress() {
    return myProgress;
  }
}
