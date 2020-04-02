package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.api.engine.util.SyncNotAllowedException;
import com.almworks.api.engine.util.SynchronizationProgress;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.QueryURL;
import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import com.almworks.bugzilla.provider.sync.Synchronization;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.util.SyncAttributes;
import com.almworks.spi.provider.*;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.progress.Progress;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.CountDownLatch;

abstract class BugzillaSyncTask extends AbstractSyncTask {
  protected final BugzillaContext myContext;
  protected final Synchronizer mySynchronizer;
  private volatile ProgressComponentWrapper myProgressComponent;

  public BugzillaSyncTask(BugzillaContext context, ComponentContainer subcontainer) {
    super(subcontainer);
    myContext = context;
    Engine engine = context.getActor(Engine.ROLE);
    assert engine != null; 
    mySynchronizer = engine.getSynchronizer();
  }

  protected void runSynchronization(BugzillaAccessPurpose purpose, SynchroState synchroState, Progress progress)
    throws ConnectorException, ConnectionNotConfiguredException
  {
    DetachComposite requestLife = new DetachComposite();
    Log.debug(this + ": starting sync");
    clearProblems();
    BugzillaIntegration integration = myContext.getIntegration(purpose);
    integration.setFeedbackHandler(getFeedbackHandler());
    integration.setCancelFlag(requestLife, myCancelFlag);
    myProgressComponent = new SynchronizationProgress(progress, myState);

    Synchronization sync = new Synchronization(myContext, synchroState, myCancelFlag, myProblems, myLastCommittedCN, purpose, progress);
    try {
      myState.setValue(State.WORKING);
      sync.synchronize();
      waitForProgress(progress);
      myState.setValue(myCancelFlag.isCancelled() ? State.CANCELLED : !myProblems.isEmpty() ? State.FAILED : State.DONE);
    } catch (ConnectorException e) {
      if (myCancelFlag.isCancelled() && !(e instanceof SyncNotAllowedException)) {
        Log.debug(this + ": synchronization cancelled", e);
        myState.setValue(State.CANCELLED);
      } else {
        Log.debug(this + ": synchronization failed", e);
        myProblems.add(new HttpConnectionProblem(myContainer, e));
        myState.setValue(State.FAILED);
        throw e;
      }
    } catch (InterruptedException e) {
      notifyInterruptedException(e);
    } finally {
      progress.setDone();
      requestLife.detach();
    }
  }

  protected final void waitForProgress(final Progress progress) throws InterruptedException {
    if (progress.isDone()) return;
    final DetachComposite life = new DetachComposite();
    setDoneIfCancelled(life, progress);
    final CountDownLatch progressDone = new CountDownLatch(1);
    ChangeListener progressListener = new ChangeListener() {
      @Override
      public void onChange() {
        if (progress.isDone()) {
          life.detach();
          progressDone.countDown();
        }
      }
    };
    progress.getModifiable().addChangeListener(life, ThreadGate.LONG_QUEUED, progressListener);
    progressListener.onChange();
    progressDone.await();
  }

  private void setDoneIfCancelled(DetachComposite life, final Progress progress) {
    myCancelFlag.getModel().getEventSource().addStraightListener(life, new ScalarModel.Adapter<Boolean>() {
      @Override
      public void onScalarChanged(ScalarModelEvent<Boolean> event) {
        if (Boolean.TRUE.equals(event.getNewValue())) progress.setDone();
      }
    });
  }

  protected void waitForConnectionReady() throws InterruptedException {
    ConnectionState state = myContext.getState().waitValue(ConnectionState.STABLE);
    if (!state.isReady())
      cancel();
  }

  protected Set<Integer> runRemoteQuery(QueryURL url, Progress progressSink)
    throws ConnectorException, ConnectionNotConfiguredException
  {
    DetachComposite requestLife = new DetachComposite();
    Set<Integer> result= Collections15.hashSet();
    try {
      BugzillaIntegration integration = myContext.getIntegration(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD);
      integration.setCancelFlag(requestLife, myCancelFlag);
      integration.setFeedbackHandler(getFeedbackHandler());
      Collection<BugInfoMinimal> infos = integration.loadQuery(url, progressSink);
      for (BugInfoMinimal info : infos) {
        result.add(info.getID());
      }
    } finally {
      requestLife.detach();
    }
    return result;
  }

  protected void downloadBugs(Set<Integer> bugIDs, final Progress downloadProgress, boolean downloadDetails)
    throws ConnectorException, ConnectionNotConfiguredException
  {
    try {
      downloadProgress.setActivity("Downloading bugs");
      final int count = bugIDs.size();
      if (count > 0) {
        Log.debug("requesting info for " + count + " bugs");

        SyncType syncType = downloadDetails ? SyncType.RECEIVE_FULL : SyncType.RECEIVE_ONLY;
        SyncParameters params = SyncParameters.syncItemsByID(bugIDs, syncType);
        ItemsOnlySynchroState synchroState = new ItemsOnlySynchroState(params, myState);

        final Progress syncProgress = new Progress();
        DetachComposite progressLife = new DetachComposite();
        final List<String> errors = Collections15.arrayList();
        syncProgress.getModifiable().addAWTChangeListener(progressLife, new ChangeListener() {
          public void onChange() {
            double progress = syncProgress.getProgress();
            int downloaded = Math.min((int) Math.round(count * progress), count);
            downloadProgress.setProgress(progress, "Downloading bugs (" + downloaded + " of " + count + " downloaded)");

            int errorCount = errors.size();
            errors.clear();
            syncProgress.getErrors(errors);
            for (int i = errorCount; i < errors.size(); i++) {
              downloadProgress.addError(errors.get(i));
            }
          }
        });
        try {
          runSynchronization(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD, synchroState, syncProgress);
        } finally {
          progressLife.detach();
          downloadProgress.setDone();
        }
      } else {
        Log.debug("no bugs for download");
        downloadProgress.setDone();
      }
      myState.setValue(State.DONE);
    } finally {
      try {
        myContext.getIntegration(BugzillaAccessPurpose.IMMEDIATE_DOWNLOAD).setFeedbackHandler(null);
      } catch (Exception e) {
        // ignore
      }
    }
  }

  public BoolExpr<DP> getConnectionExpr() {
    return DPEqualsIdentified.create(SyncAttributes.CONNECTION, getConnection().getConnectionRef());
  }

  @Override
  public SpecificItemActivity getSpecificActivityForItem(long itemId, @Nullable Integer serverId) {
    return SpecificItemActivity.OTHER;
  }

  @Override
  public ProgressComponentWrapper getProgressComponentWrapper() {
    return myProgressComponent;
  }
}
