package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.CancelledException;
import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.*;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.bugzilla.provider.RemoteQueryImpl;
import com.almworks.util.L;
import com.almworks.util.Pair;
import com.almworks.util.commons.Condition;
import org.almworks.util.Log;

import java.util.Collection;
import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
class TaskSyncRemoteQueries extends LinearProgressTask {
  public TaskSyncRemoteQueries(SyncController controller) {
    super(controller, "load-remote-queries", L.progress("Downloading remote queries"), 2000, true);
  }

  public void doRun() throws ConnectorException {
    Log.debug("remote queries sync starts");
    List<Pair<String, String>> queries = getIntegration().loadSavedSearches();
    updateRemoteQueries(queries);
  }

  private void updateRemoteQueries(List<Pair<String, String>> queries) throws CancelledException {
    BugzillaContext context = getContext();
    Collection<RemoteQuery> knownQueries = context.getRemoteQueries().copyCurrent();
    Log.debug("there are " + knownQueries.size() + " known remote queries now");
    Collection<RemoteQuery> writable = context.getRemoteQueries().getWritableCollection();
    int count = queries.size();
    float inc = count == 0 ? 0F : 0.9F / count;
    Log.debug("got " + count + " remote queries from server");
    for (int i = 0; i < count; i++) {
      progress(inc * i);
      Pair<String, String> pair = queries.get(i);
      checkCancelled();
      final String queryName = pair.getFirst();
      final String queryURL = pair.getSecond();
      RemoteQuery knownQuery = new Condition<RemoteQuery>() {
        public boolean isAccepted(RemoteQuery remoteQuery) {
          return remoteQuery.getQueryName().equals(queryName);
        }
      }.detect(knownQueries.iterator());
      if (knownQuery != null) {
        Log.debug("removing query " + queryName);
        writable.remove(knownQuery);
        knownQueries.remove(knownQuery);
      }
      Log.debug("creating query " + queryName);
      writable.add(new RemoteQueryImpl(context, queryName, queryURL));
    }
    // remove others
    for (RemoteQuery knownQuery : knownQueries) {
      checkCancelled();
      Log.debug("removing query " + knownQuery.getQueryName());
      writable.remove(knownQuery);
    }
    Log.debug("remote queries sync finished");
  }

  public boolean isApplicable(SyncParameters syncParameters) {
    return
      syncParameters.get(SyncParameter.INITIALIZE_CONNECTION) != null ||
      syncParameters.get(SyncParameter.ALL_ITEMS) != null;
  }
}
