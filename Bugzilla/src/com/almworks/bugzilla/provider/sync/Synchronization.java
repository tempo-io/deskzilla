package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.sync.download.TaskDownload;
import com.almworks.bugzilla.provider.sync.upload.TaskUploadItems;
import com.almworks.spi.provider.CancelFlag;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.model.*;
import com.almworks.util.progress.Progress;
import com.almworks.util.progress.ProgressWeightHelper;
import org.almworks.util.*;
import org.almworks.util.detach.Detach;
import org.jetbrains.annotations.*;

import java.util.List;

public class Synchronization implements SyncController {
  private final SetHolderModel<SyncProblem> myProblemsCollection;
  private final String myName;
  private final SyncData myData;

  private final List<Task> myAlgorithm = Collections15.arrayList();

  private ConnectorException myException = null;

  private final ProgressWeightHelper myProgressHelper = new ProgressWeightHelper();
  private final Synchronizer mySynchronizer;

  public Synchronization(BugzillaContext context, SynchroState synchroState, CancelFlag cancelFlag,
    SetHolderModel<SyncProblem> problems,
    BasicScalarModel<Long> lastCommittedCN, BugzillaAccessPurpose purpose, Progress progress)
    throws ConnectionNotConfiguredException
  {
    progress = progress == null ? new Progress() : progress;

    myProblemsCollection = problems;

    myData = new SyncData(context, synchroState, cancelFlag, lastCommittedCN, purpose);
    myName = context.getConfiguration().getValue().getConnectionName() + ":" + purpose + "[" +
      Integer.toHexString(hashCode()) + "]";

    initializeAlgorithm();
    myProgressHelper.insertTo(progress);
    Engine engine = context.getActor(Engine.ROLE);
    assert engine != null;
    mySynchronizer = engine.getSynchronizer();
  }

  private void initializeAlgorithm() {
    addTaskStep(new TaskBuildBugBoxes(this));
    if (myData.getParameters().shouldLoadMeta()) {
      addTaskStep(new TaskSyncEnum(this));
      addTaskStep(new TaskSyncRemoteQueries(this));
      addTaskStep(new TaskVoteSync(this));
      addTaskStep(new TaskFlagSync(this));
    }
    addTaskStep(new TaskLoadRemoteChanges(this));
    addTaskStep(new TaskDownload(this));
    addTaskStep(new TaskUploadItems(this));
  }

  private void addTaskStep(Task task) {
    if (task.isApplicable(myData.getParameters())) {
      myAlgorithm.add(task);
      myProgressHelper.addSource(task.getProgress(), task.getDuration());
    }
  }

  @NotNull
  public SyncData getData() {
    return myData;
  }

  public void runTask(Task task) throws CancelledException {
    myData.getCancelFlag().checkCancelled();
    task.runTask();
  }

  public synchronized void onIntegrationException(ConnectorException e) {
//    Log.warn(this + ": exception in task", e);
    if (myException == null)
      myException = e;
  }

  public void onProblem(SyncProblem problem, BugBox box) {
    long item = 0L;
    ItemSyncProblem problemAsIsp = problem instanceof ItemSyncProblem ? (ItemSyncProblem)problem : null;
    if (box != null) {
      item = box.getItem();
      assert problemAsIsp != null && problemAsIsp.getItem() == item;
      box.setProblematic();
    } else if (problemAsIsp != null) {
      item = problemAsIsp.getItem();
    }
    if (item > 0L) noProblemsForItem(item, true);
    if (problemAsIsp != null) {
      problemAsIsp.addToCollection(myProblemsCollection);
    } else {
      myProblemsCollection.add(problem);
    }
  }

  public void noProblemsForItem(long item, boolean clearSeriousProblem) {
    for (ItemSyncProblem problem : mySynchronizer.getItemProblems(item)) {
      if (clearSeriousProblem || !problem.isSerious()) {
        problem.disappear();
      }
    }
  }

  public void synchronize() throws ConnectorException {
    Log.debug(this + " starts");
    Detach detach = Detach.NOTHING;
    try {
      myData.getContext().lockIntegration(this);
    } catch (InterruptedException e) {
      throw new RuntimeInterruptedException(e);
    }
    try {
      detach = listenConnectionState();
      Task[] steps = myAlgorithm.toArray(new Task[myAlgorithm.size()]);
      for (Task step : steps) {
        runTask(step);
        if (myException != null) throw myException;
        checkState();
      }
      finish();
    } finally {
      try {
        detach.detach();
      } catch (Exception e) {
        Log.debug(e);
      }
      myData.getContext().unlockIntegration(this);
      Log.debug(this + " finished");
    }
  }

  private Detach listenConnectionState() {
    ScalarModel<ConnectionState> connectionState = myData.getContext().getConnection().getState();
    return connectionState.getEventSource().addStraightListener(new ScalarModel.Adapter<ConnectionState>() {
      public void onScalarChanged(ScalarModelEvent<ConnectionState> event) {
        if (event.getNewValue().isDegrading())
          myData.getCancelFlag().setValue(Boolean.TRUE);
      }
    });
  }

  private void finish() {
    cleanItemProblems();
  }

  private void cleanItemProblems() {
    List<BugBox> boxes = myData.getBugBoxes();
    for (BugBox box : boxes) {
      if (box.getSyncType() == SyncType.RECEIVE_AND_SEND) {
        long item = box.getItem();
        if (item > 0 && !box.isProblematic()) noProblemsForItem(item, true);
      }
    }
  }

  private void checkState() throws CancelledException {
    myData.getCancelFlag().checkCancelled();

    ConnectionState state = myData.getContext().getState().getValue();
    if (!state.isReady())
      throw new CancelledException();
  }

  public String toString() {
    return myName;
  }
}
