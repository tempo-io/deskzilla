package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.constraint.Constraint;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.SyncTask;
import com.almworks.bugzilla.integration.QueryURLBuilder;
import com.almworks.bugzilla.integration.data.BooleanChart;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.items.api.*;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.L;
import com.almworks.util.Terms;
import com.almworks.util.commons.LongObjFunction2;
import com.almworks.util.commons.Procedure;
import com.almworks.util.progress.Progress;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * @author Vasya
 */
public class ItemViewSynchronizer extends BugzillaSyncTask {
  private final ExecutorService myExecutor;
  private final Constraint myConstraint;
  private final DBFilter myItemView;
  private final String myQueryName;
  private final Procedure<SyncTask> myRunFinally;

  public ItemViewSynchronizer(ExecutorService executor, @NotNull Constraint constraint, DBFilter itemView, String queryName, BugzillaContext context,
    ComponentContainer subcontainer, Procedure<SyncTask> runFinally)
  {
    super(context, subcontainer);
    myExecutor = executor;
    myConstraint = constraint;
    myItemView = itemView;
    myQueryName = queryName;
    myRunFinally = runFinally;
  }

  public void executeTask() {
    myExecutor.submit(new Runnable() {
      public void run() {
        doRun();
      }
    });
  }

  public String toString() {
    return "sync " + myQueryName;
  }

  private void doRun() {
    Log.debug("running " + this);
    try {
//      Convertor producer = createActivityProducer("Loading " + Terms.query);
      Progress progress = new Progress("AVS");
      myProgress.delegate(progress);
      Progress preparing = progress.createDelegate(0.01F, "AVS-P", "Starting Bugzilla search");
      Progress runningQuery = progress.createDelegate(0.29F, "AVS-Q", "Running search on Bugzilla");
      Progress downloadBugs = progress.createDelegate(0.7F, "AVS-D");

      preparing.setProgress(0.1F);
      waitForConnectionReady();

      preparing.setProgress(0.2F);
      BooleanChart queryParam = createBooleanChart();

      preparing.setProgress(0.4F);
      QueryURLBuilder url = BugzillaUtil.buildRemoteQuery(queryParam, myContext);

      preparing.setDone();
      checkCancelled();

      Set<Integer> bugIDs = runRemoteQuery(url, runningQuery);
      checkCancelled();
      Log.debug("got " + bugIDs.size() + " ids from server");

      addLocalViewItemIDs(bugIDs);
      checkCancelled();

      downloadBugs(bugIDs, downloadBugs, false);
    } catch (InterruptedException e) {
      notifyInterruptedException(e);
    } catch (ConnectorException e) {
      notifyException(e);
    } catch (ConnectionNotConfiguredException e) {
      // connection is being closed?
      myState.setValue(SyncTask.State.CANCELLED);
    } finally {
      notifyTaskDone();

      try {
        detach();
      } catch (Exception e) {
        // ignore
      }

      if (myRunFinally != null)
        myRunFinally.invoke(this);
    }
  }

  private void addLocalViewItemIDs(final Set<Integer> bugIDs) {
    Database.require().readForeground(new ReadTransaction<Void>() {
      @Override
      public Void transaction(final DBReader reader) throws DBOperationCancelledException {
        myItemView
          .filter(getConnectionExpr())
          .query(reader)
          .fold(bugIDs, new LongObjFunction2<Set<Integer>>() {
            @Override
            public Set<Integer> invoke(long a, Set<Integer> b) {
              final Integer id = reader.getValue(a, Bug.attrBugID);
              if(id != null) {
                b.add(id);
              }
              return b;
            }
          });
        return null;
      }
    }).waitForCompletion();
  }

  private BooleanChart createBooleanChart() {
    return Database.require().readForeground(new ReadTransaction<BooleanChart>() {
      @Override
      public BooleanChart transaction(DBReader reader) throws DBOperationCancelledException {
        return new BooleanChartMaker(myContext.getConnection()).createBooleanChart(myConstraint, reader);
      }
    }).waitForCompletion();
  }
  
  public String getTaskName() {
    return L.progress("Synchronizing " + Terms.query + " " + myQueryName);
  }
}
