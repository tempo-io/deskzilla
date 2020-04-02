package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.*;
import com.almworks.api.engine.util.SyncNotAllowedException;
import com.almworks.api.engine.util.SynchronizationProgress;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.provider.sync.Synchronization;
import com.almworks.items.api.Database;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.spi.provider.HttpConnectionProblem;
import com.almworks.util.commons.Procedure2;
import com.almworks.util.exec.ThreadFactory;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import org.almworks.util.Log;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;
import org.picocontainer.Startable;
import util.concurrent.SynchronizedBoolean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

public class BugzillaSynchronizer extends BugzillaSyncTask implements ConnectionSynchronizer, Startable {
  private final SynchronizedBoolean mySyncRunning = new SynchronizedBoolean(false);
  private final SynchroState mySynchroState;
  private final ProgressComponentWrapper myProgressComponent;
  private final DetachComposite myLifeDetach = new DetachComposite();
  private final ExecutorService mySyncRunner;

  public BugzillaSynchronizer(BugzillaContext context, ComponentContainer subcontainer) {
    super(context, subcontainer);
    mySynchroState = subcontainer.instantiate(FullSynchroState.class);
    myProgressComponent = new SynchronizationProgress(myProgress, mySynchroState.getSyncStateModel());
    mySyncRunner = ThreadFactory.newSingleThreadExecutor(context.getConnection().getConnectionID());
  }

  public ProgressComponentWrapper getProgressComponentWrapper() {
    return myProgressComponent;
  }

  public ScalarModel<State> getState() {
    return mySynchroState.getSyncStateModel();
  }

  public void synchronize(final SyncParameters parameters) {
    doSynchronize(parameters, null);
  }

  public boolean doSynchronize(final SyncParameters parameters, final Procedure2<Boolean, String> runOnFinish) {
    boolean success = mySyncRunning.commit(false, true);
    if (!success) {
      // sync already running
      return false;
    }
    try {
      mySyncRunner.submit(new SyncRunnable(parameters, runOnFinish));
    } catch (RejectedExecutionException e) {
      Log.error(new Throwable(mySyncRunner.isShutdown() + " " + mySyncRunner.isTerminated(), e));
      return false;
    }
    return true;
  }

  /**
   * Use synchronize() instead
   */
  protected void executeTask() {
    throw new UnsupportedOperationException();
  }

  private SyncParameters adaptSyncParameters(final SyncParameters parameters) {
    SyncParameters usedParameters = parameters;
    Map map = usedParameters.toMap();
    if (map.containsKey(SyncParameter.EXACT_CONNECTIONS)) {

      if (!map.containsKey(SyncParameter.EXACT_ITEMS) && !map.containsKey(SyncParameter.EXACT_ITEMS_BY_ID)
        && !map.containsKey(SyncParameter.ALL_ITEMS) && !map.containsKey(SyncParameter.CHANGED_ITEMS)) {

        // in this case we're asked for sync only for certain connections, but we were not given any other
        // parameters
        Map<Connection, SyncType> connections = usedParameters.get(SyncParameter.EXACT_CONNECTIONS);
        SyncType syncType = connections.get(myContext.getConnection());
        if (syncType != null)
          usedParameters = usedParameters.merge(SyncParameters.all(syncType));
      }
    }
    return usedParameters;
  }

  public void subscribe() {
    myLifeDetach.add(getState().getEventSource().addStraightListener(new ScalarModel.Adapter<State>() {
      public void onScalarChanged(ScalarModelEvent<State> event) {
        if (myContext.getState().getValue() == ConnectionState.READY)
          fireChanged();
      }
    }));
  }

  public void start() {
    subscribe();
  }

  public void stop() {
    cancel();
    mySyncRunner.shutdown();
    myLifeDetach.detach();
  }

  public String getTaskName() {
    return myContext.getConfiguration().getValue().getConnectionName();
  }

  @Override
  public SpecificItemActivity getSpecificActivityForItem(long itemId, @Nullable Integer serverId) {
    return SpecificItemActivity.OTHER;
  }

  private class SyncRunnable implements Runnable {
    private final SyncParameters myParameters;
    private final Procedure2<Boolean, String> myRunOnFinish;

    public SyncRunnable(SyncParameters parameters, Procedure2<Boolean, String> runOnFinish) {
      myParameters = parameters;
      myRunOnFinish = runOnFinish;
    }

    public void run() {
      DetachComposite requestLife = new DetachComposite();
      boolean result = false;
      String error = null;
      try {
        if (!myContext.getState().getValue().isReady())
          return;

        if (!myParameters.isAffectedConnection(myContext.getActor(Database.ROLE), myContext.getConnection()))
          return;

        // clean up problems
        clearProblems();
        myCancelFlag.setValue(null);
        mySynchroState.setSyncState(State.WORKING);

        BugzillaIntegration integration = myContext.getIntegration(BugzillaAccessPurpose.SYNCHRONIZATION);
        integration.setFeedbackHandler(myFeedbackHandler);
        integration.setCancelFlag(requestLife, myCancelFlag);

        SyncParameters usedParameters = adaptSyncParameters(myParameters);
        mySynchroState.update(usedParameters);
        Synchronization sync = new Synchronization(myContext, mySynchroState, myCancelFlag, myProblems,
          myLastCommittedCN, BugzillaAccessPurpose.SYNCHRONIZATION, myProgress.createDelegate("sync"));
        try {
          // todo may wait for the progress to finish, but not that necessary
          sync.synchronize();
        } finally {
          try {
            myProgress.setDone();
          } catch (Exception e) {
            // ignore
          }
        }

        mySynchroState.setSyncState(State.DONE);
        List<SyncProblem> problems = myProblems.copyCurrent();
        result = problems.isEmpty();
        if (!result)
          error = problems.get(0).getShortDescription();

      } catch (ConnectorException e) {
        if (myCancelFlag.isCancelled() && !(e instanceof SyncNotAllowedException)) {
          Log.debug("synchronization cancelled", e);
          mySynchroState.setSyncState(State.CANCELLED);
        } else {
          Log.debug("synchronization failed", e);
          myProblems.add(new HttpConnectionProblem(myContainer, e));
          mySynchroState.setSyncState(State.FAILED);
          error = e.getMediumDescription();
        }
/*
      } catch (ConfigurationException e) {
        Log.debug("synchronization failed", e);
        myState.setValue(SyncTask.State.FAILED);
*/
      } catch (ConnectionNotConfiguredException e) {
        // connection is being closed?
        mySynchroState.setSyncState(State.CANCELLED);
      } catch (RuntimeException e) {
        Log.warn("exception during sync", e);
        throw e;
      } catch (Error e) {
        Log.warn("exception during sync", e);
        throw e;
      } finally {
        try {
          myContext.getIntegration(BugzillaAccessPurpose.SYNCHRONIZATION).setFeedbackHandler(null);
        } catch (Exception e) {
          // ignore
        }

        if (myRunOnFinish != null) {
          try {
            myRunOnFinish.invoke(result, error);
          } catch (Exception e) {
            // ignore
            Log.debug("runOnFinish", e);
          }
        }

        requestLife.detach();
        mySyncRunning.set(false);
      }
    }
  }
}
