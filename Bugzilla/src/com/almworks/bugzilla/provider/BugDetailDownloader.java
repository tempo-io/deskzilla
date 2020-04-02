package com.almworks.bugzilla.provider;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.container.ComponentContainer;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.integers.*;
import com.almworks.items.api.*;
import com.almworks.spi.provider.ConnectionNotConfiguredException;
import com.almworks.util.L;
import com.almworks.util.exec.ThreadGate;
import com.almworks.util.progress.Progress;
import org.almworks.util.Collections15;
import org.almworks.util.Log;
import org.jetbrains.annotations.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class BugDetailDownloader extends BugzillaSyncTask {
  private final LongList myItems;

  public BugDetailDownloader(BugzillaContext context, ComponentContainer subcontainer, LongList items)
  {
    super(context, subcontainer);
    myItems = items;
  }

  public void executeTask() {
    ThreadGate.LONG(this).execute(new Runnable() {
      public void run() {
        doRun();
      }
    });
  }

  public String toString() {
    return "dd";
  }

  private void doRun() {
    Log.debug("running " + this);
    try {
      Progress progress = new Progress("AVS");
      myProgress.delegate(progress);
      Progress preparing = progress.createDelegate(0.01F, "AVS-P", "Starting download");
      Progress downloadBugs = progress.createDelegate(0.99F, "AVS-D");

      preparing.setProgress(0.1F);
      waitForConnectionReady();
      Set<Integer> bugIDs = buildIDs();
      checkCancelled();
      preparing.setDone();

      downloadBugs(bugIDs, downloadBugs, true);

    } catch (InterruptedException e) {
      notifyInterruptedException(e);
    } catch (ConnectorException e) {
      notifyException(e);
    } catch (ConnectionNotConfiguredException e) {
      // connection is being closed?
      myState.setValue(State.CANCELLED);
    } finally {
      notifyTaskDone();
      try {
        detach();
      } catch (Exception e) {
        // ignore
      }
    }
  }

  private Set<Integer> buildIDs() {
    return getContainer().requireActor(Database.ROLE).readBackground(new ReadTransaction<Set<Integer>>() {
      @Override
      public Set<Integer> transaction(DBReader reader) throws DBOperationCancelledException {
        final LongArray filtered = reader.query(getConnectionExpr()).filterItemsSorted(myItems);

        final LinkedHashSet<Integer> result = Collections15.linkedHashSet();
        for(final LongIterator it = filtered.iterator(); it.hasNext();) {
          final Integer id = reader.getValue(it.next(), Bug.attrBugID);
          if(id != null) {
            result.add(id);
          }
        }

        return result;
      }
    }).waitForCompletion();
  }

  public String getTaskName() {
    return L.progress("Downloading details");
  }

  @Override
  public SpecificItemActivity getSpecificActivityForItem(long itemId, @Nullable Integer serverId) {
    return myItems.contains(itemId) ? SpecificItemActivity.DOWNLOAD : SpecificItemActivity.OTHER;
  }
}