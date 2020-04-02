package com.almworks.bugzilla.provider.datalink.schema;

import com.almworks.api.application.ItemDownloadStage;
import com.almworks.api.engine.util.FixedPrimaryItemStructure;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.BugInfoForUpload;
import com.almworks.bugzilla.integration.data.ConstraintConvertor;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.bugzilla.provider.datalink.flags2.Flags;
import com.almworks.bugzilla.provider.datalink.schema.attachments.AttachmentsLink;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.bugzilla.provider.datalink.schema.custom.CustomField;
import com.almworks.bugzilla.provider.sync.BugBox;
import com.almworks.items.api.*;
import com.almworks.items.dp.*;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.util.DBNamespace;
import com.almworks.items.util.SyncAttributes;
import com.almworks.itemsync.MergeOperationsManager;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Log;
import org.almworks.util.Util;

import java.math.BigDecimal;
import java.util.*;

public class Bug implements ItemLink {
  public static final ItemLink ITEM_LINK = new Bug();

  public static final DBNamespace BUG_NS = BugzillaProvider.NS.subNs("bug");

  public static final DBItemType typeBug = BUG_NS.type();
  static {
    Bug.typeBug.initialize(SyncAttributes.IS_PRIMARY_TYPE, true);
  }

  // Attribute names are very sensitive, change them with extreme care.
  // Reason: the names are used as ModelKey names, which are searched by names in several cases. Namely:
  // - Saved formulas for queries
  // - Default queries (formulas)
  // - Default columns
  // - GUI form designer: ModelKey names are used as component names to automatically bind a default UIController (if a specific one is not bound in Bugzilla{Create,Edit}View).
  // - Custom workflow actions
  // In all those places, current style is lowercase with underscores, so let's stick with that style for names

  public static final DBAttribute<Integer> attrBugID = BUG_NS.integer("id", "id", false);

  public static final DBAttribute<String> attrAlias = BUG_NS.string("alias", "alias", true);
  public static final DBAttribute<String> attrStatusWhiteboard = BUG_NS.string("statusWhiteboard", "status_whiteboard", true);
  public static final DBAttribute<String> attrUrl = BUG_NS.string("url", "url", true);
  public static final DBAttribute<String> attrSummary = BUG_NS.string("summary", "summary", true);

  public static final DBAttribute<Long> attrReporter = SingleEnumAttribute.REPORTER.getBugAttribute();
  public static final DBAttribute<Long> attrAssignedTo = SingleEnumAttribute.ASSIGNED_TO.getBugAttribute();
  public static final DBAttribute<Long> attrQaContact = SingleEnumAttribute.QA_CONTACT.getBugAttribute();
  public static final DBAttribute<Long> attrModificationAuthor = SingleEnumAttribute.MODIFICATION_AUTHOR.getBugAttribute();

  public static final DBAttribute<Date> attrCreationTime = BUG_NS.date("creationTimestamp", "creation_timestamp", false);
  public static final DBAttribute<Date> attrModificationTime = BUG_NS.date("modificationTimestamp", "modification_timestamp", false);
  public static final DBAttribute<String> attrDeltaTs = BUG_NS.string("deltaTs", "Delta TS", false);

  public static final DBAttribute<BigDecimal> attrEstimatedTime = BUG_NS.decimal("estimatedTime", "estimated_time", true);
  public static final DBAttribute<BigDecimal> attrRemainingTime = BUG_NS.decimal("remainingTime", "remaining_time", true);
  public static final DBAttribute<BigDecimal> attrActualTime = BUG_NS.decimal("actualTime", "actual_time", true);
  public static final DBAttribute<BigDecimal> attrTotalActualTime = BUG_NS.decimal("totalActualTime", "total_actual_time", false);
  public static final DBAttribute<Date> attrDeadline = BUG_NS.date("deadline", "deadline", true);

  public static final DBAttribute<Integer> attrClassificationId = BUG_NS.integer("classificationId", "classification_id", false);

  public static final DBAttribute<Set<Long>> attrCcList = MultiEnumAttribute.CC.getBugAttribute();
  public static final DBAttribute<Set<Long>> attrKeywords = BUG_NS.linkSet("keywords", "keywords", true);
  public static final DBAttribute<List<String>> attrSeeAlso = BUG_NS.stringList("seeAlso", "see_also", true);

  public static final DBAttribute<Set<Long>> attrBlockedBy = BUG_NS.linkSet("blockedBy", "blocked_by", true);
  public static final DBAttribute<Set<Long>> attrBlocks = BUG_NS.linkSet("blocks", "blocks", true);
  public static final DBAttribute<Long> attrDuplicateOf = BUG_NS.link("duplicateOf", "duplicate_of", true);

  public static final DBAttribute<Boolean> attrCcListAccessible = BUG_NS.bool("ccListAccessible", "cc_list_accessible?", false);
  public static final DBAttribute<Boolean> attrReporterAccessible = BUG_NS.bool("reporterAccessible", "reporter_accessible?", false);
  public static final DBAttribute<Boolean> attrEverConfirmed = BUG_NS.bool("everConfirmed", "ever_confirmed?", false);

  public static final DBAttribute<Long> attrStatus = SingleEnumAttribute.STATUS.getBugAttribute();
  public static final DBAttribute<Long> attrResolution = SingleEnumAttribute.RESOLUTION.getBugAttribute();
  public static final DBAttribute<Long> attrClassification = SingleEnumAttribute.CLASSIFICATION.getBugAttribute();
  public static final DBAttribute<Long> attrVersion = SingleEnumAttribute.VERSION.getBugAttribute();

  public static final DBAttribute<Long> attrProduct = SingleEnumAttribute.PRODUCT.getBugAttribute();

  public static final DBAttribute<Long> attrComponent = SingleEnumAttribute.COMPONENT.getBugAttribute();
  public static final DBAttribute<Long> attrPlatform = SingleEnumAttribute.PLATFORM.getBugAttribute();
  public static final DBAttribute<Long> attrOperatingSystem = SingleEnumAttribute.OS.getBugAttribute();
  public static final DBAttribute<Long> attrPriority = SingleEnumAttribute.PRIORITY.getBugAttribute();
  public static final DBAttribute<Long> attrSeverity = SingleEnumAttribute.SEVERITY.getBugAttribute();
  public static final DBAttribute<Long> attrTargetMilestone = SingleEnumAttribute.TARGET_MILESTONE.getBugAttribute();

  public static final FixedPrimaryItemStructure STRUCTURE =
    new FixedPrimaryItemStructure(typeBug, CommentsLink.attrMaster, Flags.AT_FLAG_MASTER, AttachmentsLink.attrMaster);
  public static final BoolExpr<DP> IS_BUG = DPEqualsIdentified.create(DBAttribute.TYPE, typeBug)
    .and(DPNotNull.create(SyncAttributes.IS_PROTOTYPE).negate());

  static {
    SyncSchema.setDecimalScale(2, attrEstimatedTime, attrRemainingTime, attrActualTime, attrTotalActualTime);
  }

  public static void registerLinks(CommonMetadata.LinksCollector collector) {
    collector.link(new IDLink<Integer>(attrBugID));

    collector.linkText(attrAlias, BugzillaAttribute.ALIAS);
    collector.linkText(attrSummary, BugzillaAttribute.SHORT_DESCRIPTION);
    collector.linkText(attrStatusWhiteboard, BugzillaAttribute.STATUS_WHITEBOARD);

    collector.link(new DecimalLink(attrEstimatedTime, BugzillaAttribute.ESTIMATED_TIME, false, false));
    collector.link(new DecimalLink(attrRemainingTime, BugzillaAttribute.REMAINING_TIME, false, false));
    collector.link(new DateLink(attrDeadline, BugzillaAttribute.DEADLINE, false,
      ConstraintConvertor.DateConvertor.createConvertorMap(
        new ConstraintConvertor.DateConvertor("deadline", true)), false, false));

    // todo - make a single link, that would remember classification_id for the classification
    collector.linkScalar(attrClassificationId, BugzillaAttribute.CLASSIFICATION_ID);
    collector.link(new DateLink(attrCreationTime, BugzillaAttribute.CREATION_TIMESTAMP, false,
      ConstraintConvertor.DateConvertor.createConvertorMap(ConstraintConvertor.CREATION_TS), true, true));
    collector.link(new DateLink(attrModificationTime, BugzillaAttribute.MODIFICATION_TIMESTAMP, false,
        ConstraintConvertor.DateConvertor.createConvertorMap(ConstraintConvertor.DELTA_TS), true, true));

    collector.link(new DeltaTsLink(attrDeltaTs));
    collector.link(new URLLink(attrUrl));

    SingleEnumAttribute.REPORTER.registerRefLink(collector);
    SingleEnumAttribute.ASSIGNED_TO.registerRefLink(collector);
    SingleEnumAttribute.QA_CONTACT.registerRefLink(collector);
    SingleEnumAttribute.MODIFICATION_AUTHOR.registerRefLink(collector);
    collector.link(CCLink.INSTANCE);

    collector.link(new BugReferenceArrayLink(attrBlockedBy, BugzillaAttribute.BLOCKED_BY, false));
    collector.link(new BugReferenceArrayLink(attrBlocks, BugzillaAttribute.BLOCKS, false));

    collector.link(new BooleanOneZeroLink(attrReporterAccessible, BugzillaAttribute.REPORTER_ACCESSIBLE, false));
    collector.link(new BooleanOneZeroLink(attrCcListAccessible, BugzillaAttribute.CCLIST_ACCESSIBLE, false));
    collector.link(new BooleanOneZeroLink(attrEverConfirmed, BugzillaAttribute.EVER_CONFIRMED, false));

    SingleEnumAttribute.STATUS.registerRefLink(collector);
    SingleEnumAttribute.RESOLUTION.registerRefLink(collector);
    SingleEnumAttribute.CLASSIFICATION.registerRefLink(collector);
    SingleEnumAttribute.VERSION.registerRefLink(collector);
    SingleEnumAttribute.COMPONENT.registerRefLink(collector);
    SingleEnumAttribute.PLATFORM.registerRefLink(collector);
    SingleEnumAttribute.OS.registerRefLink(collector);
    SingleEnumAttribute.PRIORITY.registerRefLink(collector);
    SingleEnumAttribute.SEVERITY.registerRefLink(collector);
    SingleEnumAttribute.TARGET_MILESTONE.registerRefLink(collector);
    SingleEnumAttribute.PRODUCT.registerRefLink(collector);

    final DuplicateOfLink duplicateOfLink = new DuplicateOfLink(attrDuplicateOf);
    collector.link(duplicateOfLink);

    collector.link(new WorkflowLink(SingleEnumAttribute.STATUS, SingleEnumAttribute.RESOLUTION, SingleEnumAttribute.ASSIGNED_TO, duplicateOfLink));
  }

  @Override
  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff change, BugInfoForUpload updateInfo)
    throws UploadNotPossibleException
  {
    for (DataLink link : CommonMetadata.ATTRIBUTE_LINKS) {
      link.buildUploadInfo(prepare, change, updateInfo);
    }
  }

  @Override
  public void checkUpload(UploadDrain drain, BugBox box) {
    ItemVersionCreator creator = drain.setAllDone(box.getItem());
    creator.setAlive();
  }

  public static DBAttribute getDBAttribute(BugzillaAttribute bzAttribute) {
    final BugzillaAttributeLink link = CommonMetadata.ATTR_TO_LINK.get(bzAttribute);
    if (link != null) {
      return link.getWorkspaceAttribute();
    } else {
      assert false : bzAttribute;
      return null;
    }
  }

  public static RemoteSearchable findSearchByAttribute(DBAttribute attribute, DBReader reader) {
    // Connection must be started!
    for (DataLink link : CommonMetadata.ATTRIBUTE_LINKS) {
      if (link instanceof AttributeLink) {
        if (attribute.equals(((AttributeLink) link).getWorkspaceAttribute())) {
          return (AttributeLink) link;
        }
      }
    }
    RemoteSearchable cfSearch = CustomField.getRemoteSearch(reader, attribute);
    if (cfSearch != null) return cfSearch;
    if (VotesLink.votes.equals(attribute)) return CommonMetadata.votesLink;
    if (Flags.isFlagAttribute(attribute)) return Flags.REMOTE_SEARCH;
    return null;
  }

  public static void updatePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
    for (DataLink dataLink : CommonMetadata.ATTRIBUTE_LINKS) {
      dataLink.initializePrototype(prototype, pm);
    }
  }

  public static void registerMergers(MergeOperationsManager mm) {
    MergeOperationsManager.Builder builder = mm.buildOperation(typeBug);
    // default merge:
    // attrActualTime
    // attrAlias
    // attrAssignedTo
    // attrUrl
    // attrCcListAccessible
    // attrClassification
    // attrClassificationId
    // attrDeadline
    // attrDeltaTs
    // attrDuplicateOf
    // attrEstimatedTime
    // attrEverConfirmed
    // attrOperatingSystem
    // attrPlatform
    // attrPriority
    // attrProduct
    // attrQaContact
    // attrRemainingTime
    // attrReporterAccessible
    // attrResolution
    // attrSeverity
    // attrStatus
    // attrStatusWhiteboard
    // attrSummary
    // attrTargetMilestone
    // attrVersion
    // myComponentLink
    builder.uniteSetValues(Group.AVAILABLE_GROUPS);
    builder.mergeLongSets(
      Group.CONTAINING_GROUPS,
      attrBlocks,
      attrBlockedBy,
      attrCcList,
      attrKeywords);
    builder.mergeStringSets(attrSeeAlso);
    builder.discardEdit(
      attrCreationTime,
      attrModificationTime,
      attrBugID,
      attrReporter,
      attrModificationAuthor);
    builder.addCustom(VotesLink.AUTO_MERGE);
    builder.addCustom(CustomField.AUTO_MERGE);
    builder.addConflictGroup(
      attrAlias,
      attrAssignedTo,
      attrUrl,
      attrComponent,
      attrDeadline,
      attrOperatingSystem,
      attrPlatform,
      attrPriority,
      attrProduct,
      attrQaContact,
      attrResolution,
      attrSeverity,
      attrSummary,
      attrStatus,
      attrStatusWhiteboard,
      attrTargetMilestone,
      attrVersion);
    builder.addConflictGroup(attrEstimatedTime, attrRemainingTime);
    builder.finish();
  }

  public static ItemProxy createBugProxy(final Long connection, final String bugId) {
    return createBugProxy(connection, Util.toInt(bugId, -1));
  }

  public static ItemProxy createBugProxy(final Long connection, final int bugId) {
    if (connection == null || bugId <= 0) return ItemProxy.NULL;
    return new BugProxy(bugId, connection);
  }

  public static class BugProxy implements ItemProxy {
    private final int myBugId;
    private final Long myConnection;

    private BugProxy(int bugId, Long connection) {
      myBugId = bugId;
      myConnection = connection;
      if (connection == null) Log.error("Null connection for BugId: " + bugId);
    }

    @Override
    public long findOrCreate(DBDrain drain) {
      long item = findItem(drain.getReader());
      if (item > 0) return item;
      item = createBug(drain, myBugId, myConnection);
      Log.debug("creating dummy bug " + myBugId);
      return item;
    }

    private static long createBug(DBDrain drain, int bugId, Long connection) {
      ItemVersionCreator bug = drain.createItem();
      bug.setValue(DBAttribute.TYPE, typeBug);
      bug.setValue(SyncAttributes.CONNECTION, connection);
      bug.setValue(attrBugID, bugId);
      bug.setValue(SyncAttributes.ITEM_DOWNLOAD_STAGE, ItemDownloadStage.DUMMY.getDbValue());
      return bug.getItem();
    }

    @Override
    public long findItem(DBReader reader) {
      BoolExpr<DP> filter = BoolExpr.and(DPEquals.create(SyncAttributes.CONNECTION, myConnection),
          DPEqualsIdentified.create(DBAttribute.TYPE, typeBug),
          DPNotNull.create(SyncAttributes.IS_PROTOTYPE).negate());
      return reader.query(filter).getItemByKey(attrBugID, myBugId);
    }
  }
}
