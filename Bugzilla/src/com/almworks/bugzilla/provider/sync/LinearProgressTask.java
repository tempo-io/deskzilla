package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.util.L;
import com.almworks.util.progress.Progress;
import com.almworks.util.progress.ProgressSource;

public abstract class LinearProgressTask extends Task {
  protected final Progress myProgress;
  /** {@code true}: progress is automatically set to <i>done</i> right after the task is executed. <br>
   *  {@code false}: "no please, I'll set my progress myself -- either via {@link #setDone} or through my own Progress instance which I return in the overridden {@link #getProgress} method."*/
  private final boolean myImmediate;

  protected LinearProgressTask(SyncController controller, String name, String displayableName, int durationEstimate, boolean immediate)
  {
    super(controller, name, L.progress(displayableName), durationEstimate);
    myImmediate = immediate;
    myProgress = new Progress(displayableName);
  }

  protected final void progress(float progress) throws CancelledException {
    progress(progress, null);
  }

  protected final void progress(float progress, String activity) throws CancelledException {
    if (activity != null)
      myProgress.setProgress(progress, activity);
    else
      myProgress.setProgress(progress);
    checkCancelled();
  }

  public ProgressSource getProgress() {
    return myProgress;
  }

  /** Sets done for progress for the whole task. */
  protected void setDone() {
    myProgress.setDone();
  }

  protected void executeTask() throws ConnectorException, InterruptedException {
    progress(0, getDisplayableName());
    try {
      super.executeTask();
    } finally {
      if (myImmediate) myProgress.setDone();
    }
  }
}
