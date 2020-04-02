package com.almworks.bugzilla.provider;

import com.almworks.api.application.ItemsCollector;
import com.almworks.api.application.util.sources.AbstractItemSource;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.api.engine.SyncTask;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.integers.LongListIterator;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEquals;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.collections.ChangeListener;
import com.almworks.util.config.ConfigurationException;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.progress.Progress;
import org.almworks.util.*;
import org.almworks.util.detach.Detach;
import org.almworks.util.detach.DetachComposite;
import org.jetbrains.annotations.*;

import java.util.Set;
import java.util.concurrent.ExecutorService;

import static org.almworks.util.Collections15.hashSet;

public class UrlBugSource extends AbstractItemSource {
  private static final TypedKey<SyncTask> DOWNLOAD_TASK = TypedKey.create("downloadTask");
  private final Set<Integer> myMissingIds;
  private final BugzillaContextImpl myContext;
  private final ComponentContainer myConnectionContainer;
  private final ExecutorService myExecutor;
  private final Progress myLocalProgress = new Progress("Loading bugs from local database");
  private final Progress myServerProgress = new Progress("Downloading bugs from server");

  public static final int LOCAL_TO_REMOTE_PROGRESS_RATIO = 5;

  public UrlBugSource(Set<Integer> ids, BugzillaContextImpl context, ComponentContainer connectionContainer, ExecutorService executor) {
    super(UrlBugSource.class.getSimpleName());
    myMissingIds = ids;
    myContext = context;
    myConnectionContainer = connectionContainer;
    myExecutor = executor;
  }

  @Override
  public void reload(@NotNull final ItemsCollector collector) {
    final DetachComposite detach = new DetachComposite();
    Detach old = collector.putValue(DETACH, detach);
    if (old != null) old.detach();

    final Progress overallProgress = getProgressDelegate(collector);
    double localSpan = 1. / (LOCAL_TO_REMOTE_PROGRESS_RATIO + 1);
    overallProgress.delegate(myLocalProgress, localSpan);
    overallProgress.delegate(myServerProgress, 1 - localSpan);
    final double totalSize = myMissingIds.size();

    BoolExpr<DP> connectionBugs = myContext.getBugsView().filter(DPEquals.equalOneOf(Bug.attrBugID, myMissingIds)).getExpr();
    myContext.getActor(Database.ROLE).liveQuery(detach, connectionBugs, new DBLiveQuery.Listener() {
      boolean requestedServer = false;

      @Override
      public void onICNPassed(long icn) {
      }

      @Override
      public void onDatabaseChanged(DBEvent event, DBReader reader) {
        for (LongListIterator i = event.getAddedAndChangedSorted().iterator(); i.hasNext();) {
          if (stopped(collector)) return;
          long item = i.next();
          int id = Util.NN(reader.getValue(item, Bug.attrBugID), 0);
          if (myMissingIds.remove(id)) {
            collector.addItem(item, reader);
            myLocalProgress.setProgress(1 - myMissingIds.size() / totalSize);
          }
        }
        for (LongListIterator i = event.getRemovedSorted().iterator(); i.hasNext(); ) {
          if (stopped(collector)) return;
          // bugs may disappear for external reasons, e.g. concurrent removal
          // and we won't ask the server for this disappeared bug
          collector.removeItem(i.next());
        }
        if (myMissingIds.isEmpty()) {
          detach.detach();
          overallProgress.setDone();
        } else if (!requestedServer && !stopped(collector)) {
          SyncTask task = startLoadMissingIdsFromServer(collector);
          collector.putValue(DOWNLOAD_TASK, task);
          requestedServer = true;
        }
      }
    });
    reportDoneOnDownloadFailure(detach);
  }

  private void reportDoneOnDownloadFailure(final DetachComposite detach) {
    myServerProgress.getModifiable().addChangeListener(detach, ThreadGate.STRAIGHT, new ChangeListener() {
      @Override
      public void onChange() {
        if (myServerProgress.isDone() && myServerProgress.getErrors(null) != null) {
          // we'll never get the bugs appear in the DB, cancel waiting
          myLocalProgress.setDone();
          detach.detach();
        }
      }
    });
  }

  private boolean stopped(ItemsCollector collector) {
    Detach detach = collector.getValue(DETACH);
    if (detach == null || detach.isDetached()) {
      getProgressDelegate(collector).setDone();
      return true;
    }
    return false;
  }

  private SyncTask startLoadMissingIdsFromServer(ItemsCollector collector) {
    final LoadBugsByIdsSyncTask task = new LoadBugsByIdsSyncTask(hashSet(myMissingIds), myServerProgress, collector);
    myExecutor.execute(new Runnable() {
      @Override
      public void run() {
        task.startTask();
      }
    });
    return task;
  }

  @Override
  public void stop(@NotNull ItemsCollector collector) {
    DetachComposite detach = collector.getValue(DETACH);
    if (detach != null) {
      detach.detach();
      collector.putValue(DETACH, null);
    }
    SyncTask downloadTask = collector.getValue(DOWNLOAD_TASK);
    if (downloadTask != null) {
      downloadTask.cancel();
      collector.putValue(DOWNLOAD_TASK, null);
    }
  }

  private String getBaseUrl() {
    OurConfiguration config = myContext.getConfiguration().getValue();
    String baseUrl = "<no URL>";
    try {
      if (config != null)
        baseUrl = config.getBaseURL();
    } catch (ConfigurationException e) {
      Log.warn(this + "\n" + e);
    }
    return baseUrl;
  }

  private class LoadBugsByIdsSyncTask extends BugzillaSyncTask {
    private final Set<Integer> myIds;
    private final Progress myProgress;
    private final ItemsCollector myCollector;

    private LoadBugsByIdsSyncTask(Set<Integer> ids, Progress progress, ItemsCollector collector) {
      super(UrlBugSource.this.myContext, myConnectionContainer);
      myIds = ids;
      myProgress = progress;
      myCollector = collector;
    }

    @Override
    protected void executeTask() {
      try {
        downloadBugs(myIds, myProgress, false);
      } catch (ConnectorException e) {
//        if (!myServerProgress.)
        myServerProgress.addError("Cannot download bugs from '" + getBaseUrl() + "': " + e.getMediumDescription());
        myServerProgress.setDone();
      } catch (ConnectionNotConfiguredException e) {
        // If connection were not ready when UrlBugSource was created, we hadn't had collected IDs from URLs. Connection is being deleted?
        Log.warn(this + "\n" + e);
        myServerProgress.addError("Cannot download bugs: connection to '" + getBaseUrl() + "' is not ready");
        myServerProgress.setDone();
      }
    }

    @Override
    public String getTaskName() {
      return "Downloading bugs from server";
    }
  }
}
