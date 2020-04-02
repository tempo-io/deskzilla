package com.almworks.bugzilla.provider.sync;

import com.almworks.api.application.ItemDownloadStage;
import com.almworks.api.connector.ConnectorException;
import com.almworks.bugzilla.integration.*;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.DataLink;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.items.sync.DBDrain;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.Pair;
import com.almworks.util.collections.MultiMap;
import org.almworks.util.Log;
import org.almworks.util.Util;

import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class SyncUtil {
  public static void updateDB(BugBox box, ItemVersionCreator creator, BugzillaContext context) {
    creator.setAlive();
    final ItemDownloadStage downloadStage = box.getFrontPageData() != null ? ItemDownloadStage.FULL : ItemDownloadStage.QUICK;
    downloadStage.setToCreator(creator);
    box.setDownloadStage(downloadStage);
    loadValuesIntoCreator(creator, box, context);
    reportWorkflow(box, context);
    reportPermissions(box, context);
    reportCustomFieldDependencies(creator, box, context);
    reportUnusedFields(creator, box, context);
  }

  private static void loadValuesIntoCreator(ItemVersionCreator bugCreator, BugBox box, BugzillaContext context) {
    BugInfo server = box.getBugInfo();
    for (DataLink link : CommonMetadata.ATTRIBUTE_LINKS) {
      link.updateRevision(context.getPrivateMetadata(), bugCreator, server, context);
    }
  }

  private static void reportWorkflow(BugBox box, BugzillaContext context) {
    BugInfo bugInfo = box.getBugInfo();
    List<StatusInfo> allowedStatusChanges = bugInfo.getAllowedStatusChanges();
    if (allowedStatusChanges != null) {
      String currentStatus = bugInfo.getValues().getScalarValue(BugzillaAttribute.STATUS, null);
      if (currentStatus != null) {
        WorkflowTracker tracker = context.getWorkflowTracker();
        tracker.reportWorkflow(currentStatus, allowedStatusChanges, bugInfo.getMarkDuplicateStatus());
      }
    }
  }

  private static void reportUnusedFields(DBDrain drain, BugBox box, BugzillaContext context) {
    FrontPageData fpd = box.getFrontPageData();
    if (fpd == null) return;
    OptionalFieldsTracker tracker = context.getOptionalFieldsTracker();
    MultiMap<String,String> formParams = fpd.getFormParameters();
    boolean changed = false;
    for (BugzillaAttribute attr : OptionalFieldsTracker.getTrackedFields()) {
      String name = BugzillaHTMLConstants.UPDATE_FORM_FIELD_NAMES_MAP.get(attr);
      if (name != null) {
        changed |= tracker.reportUnused(attr, !formParams.containsKey(name));
      } else if (attr == BugzillaAttribute.SEE_ALSO) {
        changed |= tracker.reportUnused(attr, !fpd.isSeeAlsoEnabled());
      } else if (attr == BugzillaAttribute.TOTAL_VOTES) {
        // voting can be disabled for a product (max votes=0), not entire connection
        // changed |= tracker.reportUnused(attr, !fpd.isVoteLinkFound());
      } else assert false : attr;
    }
    if (changed) context.getPrivateMetadata().updateBugPrototype(drain);
  }

  private static void reportPermissions(BugBox box, BugzillaContext context) {
    FrontPageData fpd = box.getFrontPageData();
    if (fpd == null)
      return;
    PermissionTracker tracker = context.getPermissionTracker();
    boolean timeTrackingGroup = fpd.isTimeTrackingPresent();
    tracker.setTimeTrackingAllowed(timeTrackingGroup);
    boolean seeAlsoEnabled = fpd.isSeeAlsoEnabled();
    if (seeAlsoEnabled)
      tracker.setSeeAlsoEnabled(seeAlsoEnabled);
  }

  private static void reportCustomFieldDependencies(ItemVersionCreator creator, BugBox box, BugzillaContext context) {
    FrontPageData fpd = box.getFrontPageData();
    if (fpd == null) return;
    final CustomFieldDependencies cfd = fpd.getCustomFieldDependencies();
    if (cfd == null) return;
    final Map<String, CustomFieldInfo> cfi = fpd.getCustomFieldInfo();
    if (cfi == null) return;
    CustomField.updateFieldDependencies(creator, context.getPrivateMetadata(), cfi, cfd);
  }

  public static void runExtraDownload(BugBox box, Task task) throws ConnectorException {
    runExtraDownload(box, box.getBugInfo(), task);
  }

  public static void runExtraDownload(BugBox box, BugInfo info, Task task) throws ConnectorException {
    Integer id = box.getID();
    if (id == null) {
      Log.warn(task + " called without id");
      return;
    }
    BugzillaIntegration integration = task.getIntegration();
    FrontPageData frontPageData = integration.loadBugPage(id);
    info.updateWith(frontPageData);
    task.checkCancelled();
    List<Pair<BugzillaUser, Integer>> votes = loadVotesConditionally(id, info, frontPageData, integration);
    task.checkCancelled();
    box.setFrontPageData(frontPageData);
    info.updateWithVotes(votes);
  }

  private static List<Pair<BugzillaUser, Integer>> loadVotesConditionally(Integer id, BugInfo bugInfo, FrontPageData frontPageData,
    BugzillaIntegration integration) throws ConnectorException
  {
    Integer voteCount = frontPageData.getVoteCount();
    int totalVotes = Util.toInt(bugInfo.getValues().getScalarValue(BugzillaAttribute.TOTAL_VOTES, null), -1);
    return totalVotes > 0 || (voteCount != null && voteCount > 0) ? integration.loadVotes(id) :
      Collections.<Pair<BugzillaUser, Integer>>emptyList();
  }

  public static String getSuffix(BugBox box) {
    String suffix = "";
    Integer id = box.getID();
    if (id == null) {
      long item = box.getItem();
      if (item >= 0)
        suffix = String.valueOf(item);
    } else {
      suffix = id.toString();
    }
    return suffix;
  }
}
