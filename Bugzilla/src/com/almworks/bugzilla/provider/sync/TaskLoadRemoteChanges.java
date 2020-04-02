package com.almworks.bugzilla.provider.sync;

import com.almworks.api.connector.ConnectorException;
import com.almworks.api.engine.*;
import com.almworks.bugzilla.integration.BugzillaIntegration;
import com.almworks.bugzilla.integration.QueryURLBuilder;
import com.almworks.bugzilla.integration.data.BugInfo;
import com.almworks.bugzilla.integration.data.BugInfoMinimal;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.OurConfiguration;
import com.almworks.spi.provider.util.ServerSyncPoint;
import com.almworks.util.Env;
import com.almworks.util.L;
import com.almworks.util.progress.Progress;
import org.almworks.util.*;

import java.util.*;

import static com.almworks.bugzilla.integration.QueryURL.Column.MODIFICATION_DATE_DESC;

/**
 * Loads IDs of bugs changed on server since last sync.
 *
 * @author sereda
 */
class TaskLoadRemoteChanges extends LinearProgressTask {

  public TaskLoadRemoteChanges(SyncController controller) {
    super(controller, "load-remote-changes", L.progress("Detecting changed bugs"), 5000, true);
  }

  protected void doRun() throws ConnectorException {
    SyncType syncType = getSyncParameter(SyncParameter.ALL_ITEMS);
    if (syncType == null) {
      // not loading changes if we're not doing all-sync
      return;
    }

    checkCancelled();
    boolean isSync = Util.NN(getSyncParameter(SyncParameter.UPDATE_CHANGES), Boolean.FALSE);
    Set<Integer> changes = loadRemoteChanges(isSync);
    for (Iterator<Integer> ii = changes.iterator(); ii.hasNext();) {
      Integer id = ii.next();
      getSyncData().updateBox(id, syncType);
    }
  }

  private Set<Integer> loadRemoteChanges(boolean sync) throws ConnectorException {
    SyncData data = getSyncData();
    BugzillaIntegration integration = getIntegration();
    integration.ensureVersionIsKnown(); // Ensure version is know. Required to workaround Bugzilla 4.2 time zone bug
    OurConfiguration config = getContext().getConfiguration().getValue();
    if (config == null) {
      Log.warn("cannot load changes without configuration");
      return Collections15.emptySet();
    }
    Collection<BugInfoMinimal> bugs;
    long syncTime = 0;
    if (sync) {
      ServerSyncPoint syncPoint = data.getSyncState().getSyncPoint();
      if (syncPoint != null) {
        // hack: check if load meta is requested; in that case don't use quick-check and download all issues with stepback
        // this makes possible using F5 to download all lately changed bugs if quick-check fails
        boolean useQuickCheck = !data.getParameters().shouldLoadMeta();
        if (useQuickCheck) {
          if (isNoChangesQuicklyDetected(syncPoint, integration, config)) {
            return Collections.emptySet();
          }
        }
        long stepback = getStepback(useQuickCheck);
        syncTime = syncPoint.getSyncTime() - stepback;
      }
      data.setUpdateSyncPointLater(true);
    }
    int limit = 0;
    if (syncTime < Const.DAY) {
      syncTime = integration.getLastServerResponseTime();
      if (syncTime < Const.DAY) {
        syncTime = System.currentTimeMillis();
      }
      syncTime -= getStepback(false) * 3;
      limit = 100;
    }
    Date debugSyncTime = new Date(syncTime);
    Log.debug("loading changes since " + debugSyncTime);
    QueryURLBuilder queryBuilder = integration.getURLQueryBuilder();
    BugzillaUtil.addConfigurationLimits(queryBuilder, config, syncTime);
    if (limit > 0) {
      queryBuilder.setLimit(limit);
    }
    bugs = integration.loadQuery(queryBuilder, null);

    if (bugs == null)
      return Collections15.emptySet();
    Set<Integer> ids = BugInfoMinimal.EXTRACT_ID.collectSet(bugs);
    Log.debug("loaded " + ids.size() + " ids (" + debugSyncTime + ")");
    return ids;
  }

  private long getStepback(boolean useQuickCheck) {
    // This number may be subtracted from the last mtime to maybe access some items once more, but to be sure
    // that we access all changes.
    return useQuickCheck ?
      Env.getInteger("bugzilla.reload.stepback.quick", (int) Const.MINUTE * 3) :
      Env.getInteger("bugzilla.reload.stepback", (int) Const.HOUR * 24);
  }

  private boolean isNoChangesQuicklyDetected(ServerSyncPoint syncPoint, BugzillaIntegration integration,
    OurConfiguration config) throws ConnectorException
  {
    if (syncPoint == null)
      return false;
    long syncTime = syncPoint.getSyncTime();
    if (syncTime < Const.DAY)
      return false;
    int issueId = syncPoint.getLatestIssueId();
    String issueMtime = syncPoint.getLatestIssueMTime();
    if (issueId <= 0 || issueMtime == null || issueMtime.length() == 0)
      return false;
    QueryURLBuilder queryBuilder = integration.getURLQueryBuilder();
    BugzillaUtil.addConfigurationLimits(queryBuilder, config, syncTime - getStepback(false));
    queryBuilder.setOrderBy(MODIFICATION_DATE_DESC);
    queryBuilder.setLimit(1);
    List<BugInfoMinimal> infoMinimals = integration.loadSmallQuery(queryBuilder.getURL(), null);
    if (infoMinimals == null || infoMinimals.size() != 1)
      return false;
    BugInfoMinimal infoMinimal = infoMinimals.get(0);
    Integer id = infoMinimal.getID();
    if (id == null || id != issueId)
      return false;
    Collection<BugInfo> infos = integration.loadBugDetailsOnlyIdAndMtime(new Integer[] {id}, new Progress());
    if (infos == null || infos.size() != 1)
      return false;
    BugInfo info = infos.iterator().next();
    String mtime = info.getStringMTime();
    if (mtime == null || !mtime.equals(issueMtime))
      return false;
    Log.debug("nothing changed, last bug " + id);
    return true;
  }

  public boolean isApplicable(SyncParameters syncParameters) {
    return syncParameters.get(SyncParameter.ALL_ITEMS) != null;
  }
}
