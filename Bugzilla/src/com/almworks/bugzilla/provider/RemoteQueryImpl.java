package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.RemoteQuery;
import com.almworks.api.engine.SyncTask;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.ReadonlyQueryURL;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.DBAttribute;
import com.almworks.items.api.DP;
import com.almworks.items.dp.DPEquals;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.commons.Procedure;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.events.EventSource;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.model.ScalarModel;
import com.almworks.util.model.ScalarModelEvent;
import com.almworks.util.progress.Progress;
import org.almworks.util.*;
import org.almworks.util.detach.Detach;

import java.net.MalformedURLException;
import java.util.Set;

/**
 * :todoc:
 *
 * @author sereda
 */
public class RemoteQueryImpl implements RemoteQuery {
  private final BugzillaContext myContext;
  private final String myName;
  private final String myQueryURL;

  private boolean myValid = false;
  private BoolExpr<DP> myFilter = BoolExpr.FALSE();
  private final Set<Integer> myLastQueryResult = Collections15.hashSet();

  private final Object myLock = new Object();

  public RemoteQueryImpl(BugzillaContext context, String queryName, String queryURL) {
    if (queryName == null)
      throw new NullPointerException("queryName");
    if (queryURL == null)
      throw new NullPointerException("queryURL");
    myContext = context;
    myName = queryName;
    myQueryURL = queryURL;
  }

  public String getQueryName() {
    return myName;
  }

  public SyncTask reload(Procedure<SyncTask> runFinally) {
    MyTask task = new MyTask(myContext, myContext.getContainer(), runFinally);
    task.startTask();
    return task;
  }

  public BoolExpr<DP> getFilter() {
    synchronized (myLock) {
      return myValid ? myFilter : BoolExpr.<DP>FALSE();
    }
  }

  public int getCount() {
    synchronized (myLock) {
      return myValid ? myLastQueryResult.size() : 0;
    }
  }

  public Detach getQueryUrl(final Procedure<String> queryAcceptor, final ThreadGate gate) {
    final EventSource<ScalarModel.Consumer<OurConfiguration>> eventSource = myContext.getConfiguration().getEventSource();
    return eventSource.addStraightListener(new ScalarModel.Adapter<OurConfiguration>() {
      public void onContentKnown(ScalarModelEvent<OurConfiguration> event) {
        OurConfiguration value = event.getNewValue();
        if (value == null)
          return;
        try {
          final String baseUrl = BugzillaIntegration.normalizeURL(value.getBaseURL());
          gate.execute(new Runnable() {
            public void run() {
              queryAcceptor.invoke(baseUrl + myQueryURL);
            }
          });
        } catch (ConfigurationException e) {
          Log.warn(e);
        } catch (MalformedURLException e) {
          return;
        }
        eventSource.removeListener(this);
      }
    });
  }

  public String getQueryURL() {
    return myQueryURL;
  }


  private class MyTask extends BugzillaSyncTask {
    private final Procedure<SyncTask> myRunFinally;

    public MyTask(BugzillaContext context, ComponentContainer subcontainer, Procedure<SyncTask> runFinally) {
      super(context, subcontainer);
      myRunFinally = runFinally;
    }

    public String getTaskName() {
      return "Loading remote " + Terms.query + " " + myName;
    }

    public void executeTask() {
      ThreadGate.LONG(RemoteQueryImpl.class).execute(new Runnable() {
        public void run() {
          doRun();
        }
      });
    }

    protected void init() {
      super.init();
      synchronized (myLock) {
        myLastQueryResult.clear();
        myFilter = BoolExpr.<DP>FALSE();
        myValid = false;
      }
    }

    private void doRun() {
      Log.debug("running " + this);
      try {
        Progress progress = new Progress("RQDL");
        Progress runningQuery = progress.createDelegate(0.3F, "AVS-Q", "Running search on Bugzilla");
        Progress downloadBugs = progress.createDelegate(0.7F, "AVS-D", "Downloading bugs");
        myProgress.delegate(progress);
        waitForConnectionReady();
        checkCancelled();
        Set<Integer> result = runRemoteQuery(new ReadonlyQueryURL(myQueryURL), runningQuery);
        myLastQueryResult.addAll(result);
        checkCancelled();
        Log.debug("got " + myLastQueryResult.size() + " ids from server");
        downloadBugs(myLastQueryResult, downloadBugs, false);

        fixResults();
        myState.setValue(SyncTask.State.DONE);
        // todo extract common code with other tasks that handle exceptions
      } catch (InterruptedException e) {
        myState.setValue(SyncTask.State.CANCELLED);
        throw new RuntimeInterruptedException(e);
      } catch (ConnectorException e) {
        String errmsg = L.tooltip("Cannot run Bugzilla search (" + e.getShortDescription() + ")");
        myProgress.addError(errmsg);
      } catch (ConnectionNotConfiguredException e) {
        // connection is being closed?
        myState.setValue(SyncTask.State.CANCELLED);
      } finally {
        myProgress.setDone();

        try {
          detach();
        } catch (Exception e) {
          // ignore
        }

        if (myRunFinally != null)
          myRunFinally.invoke(this);
      }
    }

    private void fixResults() {
      synchronized (myLock) {
        myValid = true;
        if (myLastQueryResult.size() == 0) {
          myFilter = BoolExpr.FALSE();
        } else {
          final Set<Integer> filterSet = Collections15.hashSet(myLastQueryResult);
          final DBAttribute<Integer> idattr = Bug.attrBugID;
          myFilter = DPEquals.equalOneOf(idattr, filterSet);
        }
      }
    }
  }
}
