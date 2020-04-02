package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.spi.provider.CancelFlag;
import com.almworks.spi.provider.util.ServerSyncPoint;
import com.almworks.util.model.BasicScalarModel;
import com.almworks.util.model.ScalarModel;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * :todoc:
 *
 * @author sereda
 */
public abstract class TaskTestFixture extends BugzillaConnectionFixture {
  protected SyncController myController;
  protected SyncData myData;
  protected List<Task> myTasksRun = Collections15.arrayList();
  protected SyncParameters mySyncParameters;
  protected Map<Integer, SyncType> myPendingBugs;
  protected ServerSyncPoint mySyncPoint;
  protected TestSynchroState mySynchroState;
  protected CancelFlag myCancelFlag;
  protected BasicScalarModel<Long> myLastCommittedWCN = BasicScalarModel.create(true, false);

  protected void setUp() throws Exception {
    super.setUp();
    clean();
    init();
    myController = new TestController();
    mySyncParameters = SyncParameters.receiveAndSendAll();
    mySynchroState = new TestSynchroState();
    myCancelFlag = new CancelFlag();
    myData =
      new SyncData(myContext, mySynchroState, myCancelFlag, myLastCommittedWCN, BugzillaAccessPurpose.SYNCHRONIZATION);
  }

  protected void clean() {
    myController = null;
    myData = null;
    myTasksRun.clear();
    super.clean();
  }

  protected class TestController implements SyncController {
    @NotNull
    public SyncData getData() {
      return myData;
    }

    public void noProblemsForItem(long item, boolean clearSeriousProblem) {
    }

    public void onIntegrationException(ConnectorException e) {
      myExceptionReported = e;
    }

    public void runTask(Task task) {
      myTasksRun.add(task);
    }

    public void onProblem(SyncProblem problem, BugBox box) {
    }
  }

  protected class TestSynchroState implements SynchroState {
    public SyncParameters extractParameters() {
      return mySyncParameters;
    }

    public Map<Integer, SyncType> extractPendingBugs() {
      return myPendingBugs;
    }

    public void setSyncPoint(ServerSyncPoint syncPoint) {
      mySyncPoint = syncPoint;
    }

    public ServerSyncPoint getSyncPoint() {
      return mySyncPoint;
    }

    public void update(SyncParameters parameters) {
      throw new UnsupportedOperationException();
    }

    public ScalarModel<SyncTask.State> getSyncStateModel() {
      throw new UnsupportedOperationException();
    }

    public void setSyncState(SyncTask.State state) {
      throw new UnsupportedOperationException();
    }
  }
}
