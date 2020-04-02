package com.almworks.bugzilla.provider.datalink;

import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.schema.Bug;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.integers.*;
import com.almworks.items.api.*;
import com.almworks.items.dp.*;
import com.almworks.items.sync.*;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;

/**
 * Special {@code DataLink} subclass for the Hours Worked attribute.
 * Recalculates total hours worked if the remote value is updated.
 * @author pzvyagin
 */
public class HoursWorkedLink implements BugzillaAttributeLink<BigDecimal> {
  private static final DBNamespace NS = BugzillaProvider.NS.subNs("hoursWorked");
  private static final DBAttribute<Boolean> attrDownloadedFromServer = NS.bool("downloadedFromServer");

  @Override
  public BugzillaAttribute getBugzillaAttribute() {
    return BugzillaAttribute.ACTUAL_TIME;
  }

  @Override
  public DBAttribute<BigDecimal> getWorkspaceAttribute() {
    return Bug.attrActualTime;
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {}

  @Override
  public void updateRevision(PrivateMetadata pm, ItemVersionCreator bugCreator, BugInfo bugInfo, @NotNull BugzillaContext context)
  {
    String stringValue = bugInfo.getValues().getScalarValue(getBugzillaAttribute(), null);
    BigDecimal value = ScalarLink.getScalarFromRemoteString(stringValue, getWorkspaceAttribute());
    bugCreator.setValue(getWorkspaceAttribute(), value);
    bugCreator.setValue(attrDownloadedFromServer, true);
  }

  @Nullable
  public BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection)
  {
    return AbstractAttributeLink.buildBooleanChartElement(
      constraint, negated, ScalarLink.getScalarBCEParameter(constraint, getWorkspaceAttribute()),
      getBugzillaAttribute());
  }

  @Override
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) {}

  @Override
  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    return null;
  }

  private static final BoolExpr<DP> LOCALLY_CHANGED = DPNotNull.create(SyncAttributes.BASE_SHADOW);
  private static final DBTrigger TOTAL_TIME_TRIGGER = new DBTrigger(
    Bug.BUG_NS.obj("totalActualTime.trigger"),
    DPReferredBy.create(CommentsLink.attrMaster, CommentsLink.IS_COMMENT.and(LOCALLY_CHANGED))
      .or(DPNotNull.create(attrDownloadedFromServer)))
  {
    @Override
    public void apply(LongList bugs, DBWriter writer) {
      for(LongIterator it = bugs.iterator(); it.hasNext();) {
        long bug = it.next();
        writer.setValue(bug, attrDownloadedFromServer, null);
        adjustTime(bug, writer);
      }
    }

    private void adjustTime(long bug, DBWriter writer) {
      final BigDecimal committed = Util.NN(writer.getValue(bug, Bug.attrActualTime), BigDecimal.ZERO);

      final LongArray comments = writer.query(
        BoolExpr.and(
          DPEquals.create(CommentsLink.attrMaster, bug), LOCALLY_CHANGED)).copyItemsSorted();

      BigDecimal uncommitted = BigDecimal.ZERO;
      for(final LongIterator it = comments.iterator(); it.hasNext();) {
        final BigDecimal time = writer.getValue(it.next(), CommentsLink.attrWorkTime);
        if(time != null) {
          uncommitted = uncommitted.add(time);
        }
      }
      writer.setValue(bug, Bug.attrTotalActualTime, committed.add(uncommitted));
    }
  };

  public static void registerTrigger(Database db) {
    db.registerTrigger(TOTAL_TIME_TRIGGER);
  }
}
