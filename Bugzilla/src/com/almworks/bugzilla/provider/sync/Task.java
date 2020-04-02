package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.SyncParameter;
import com.almworks.api.engine.SyncParameters;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.util.progress.ProgressSource;
import org.almworks.util.*;

public abstract class Task {
  protected final SyncController myController;
  protected final String myName;
  private final String myDisplayableName;
  private final int myDuration;

  public Task(SyncController controller, String name, String displayableName, int duration) {
    myDuration = duration;
    assert controller != null;
    assert name != null;
    myName = name;
    myController = controller;
    myDisplayableName = Util.NN(displayableName);
  }

  public final void runTask() {
    try {
      log("started");
      if (isApplicable(getSyncData().getParameters())) {
        executeTask();
      } else {
        log("n/a");
      }
    } catch (Throwable e) {
      Throwable t = ExceptionUtil.unwrapInvocationException(e);
      if (t instanceof InterruptedException || t instanceof RuntimeInterruptedException) {
        myController.onIntegrationException(new CancelledException(t));
        // clear interrupted status
        Thread.currentThread().interrupt();
      } else if (t instanceof ConnectorException) {
        myController.onIntegrationException((ConnectorException) t);
      } else {
        throw ExceptionUtil.rethrow(e);
      }
    } finally {
      log("finished");
    }
  }

  public final SyncData getSyncData() {
    return myController.getData();
  }
  
  public final BugzillaContext getContext() {
    return getSyncData().getContext();
  } 

  public final <T> T getSyncParameter(SyncParameter<T> parameter) {
    return getSyncData().getParameters().get(parameter);
  }

  public final BugzillaIntegration getIntegration() {
    return getSyncData().getIntegration();
  }

  private void log(String message) {
    Log.debug("[" + Thread.currentThread().getName() + "] task " + myName + " " + message);
  }

  protected void executeTask() throws ConnectorException, InterruptedException {
    doRun();
  }

  public String toString() {
    return myName;
  }

  protected abstract void doRun() throws ConnectorException, InterruptedException;

  public void checkCancelled() throws CancelledException {
    getSyncData().getCancelFlag().checkCancelled();
  }

  public abstract ProgressSource getProgress();

  public String getName() {
    return myName;
  }

  public int getDuration() {
    return myDuration;
  }

  public SyncController getController() {
    return myController;
  }

  public String getDisplayableName() {
    return myDisplayableName;
  }

  public boolean isApplicable(SyncParameters syncParameters) {
    return true;
  }

  public PrivateMetadata getPrivateMetadata() {
    return getContext().getPrivateMetadata();
  }
}
