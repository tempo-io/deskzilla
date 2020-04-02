package com.almworks.bugzilla.provider.meta;

import com.almworks.api.application.*;
import com.almworks.api.application.util.*;
import com.almworks.api.explorer.gui.ItemsListKey;
import com.almworks.api.explorer.gui.SimpleModelKey;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.bugzilla.provider.CCListModelKey;
import com.almworks.bugzilla.provider.attachments.AttachmentInfo;
import com.almworks.bugzilla.provider.attachments.AttachmentsModelKey;
import com.almworks.bugzilla.provider.comments.CommentListModelKey;
import com.almworks.bugzilla.provider.datalink.VotesLink;
import com.almworks.bugzilla.provider.datalink.flags2.FlagsModelKey;
import com.almworks.bugzilla.provider.datalink.schema.*;
import com.almworks.bugzilla.provider.meta.groups.BugGroupInfo;
import com.almworks.bugzilla.provider.meta.groups.GroupsIO;
import com.almworks.items.api.DBAttribute;
import com.almworks.util.Env;
import com.almworks.util.images.Icons;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import javax.swing.text.Document;
import javax.swing.text.StyledDocument;
import java.math.BigDecimal;
import java.util.*;

public final class BugzillaKeys {
  public static final ValueKey<Integer> id;
  public static final SimpleModelKey<StyledDocument, String> summary;
  public static final ComboBoxModelKey product;
  public static final ComboBoxModelKey component;
  public static final ComboBoxModelKey version;
  public static final ComboBoxModelKey milestone;
  public static final ComboBoxModelKey platform;
  public static final ComboBoxModelKey os;
  public static final ComboBoxModelKey priority;
  public static final ComboBoxModelKey severity;
  public static final ModelKey<Date> deadline;
  public static final ModelKey<BigDecimal> estimatedTime;
  public static final ModelKey<BigDecimal> workedTime;
  public static final ModelKey<BigDecimal> totalWorkTime;
  public static final ModelKey<BigDecimal> remainingTime;
  public static final ComboBoxModelKey status;
  public static final ComboBoxModelKey resolution;
  public static final ComboBoxModelKey reporter;
  public static final ComboBoxModelKey assignedTo;
  public static final ComboBoxModelKey qaContact;
  public static final CCListModelKey cc;
  public static final SimpleModelKey<Document, String> statusWhiteboard;
  public static final SimpleModelKey<Document, String> alias;
  public static final SimpleModelKey<Document, String> url;
  public static final ModelKey<Set<ItemKey>> keywords;
  public static final ModelKey<Date> modificationTime;
  public static final ModelKey<Date> creationTime;
  public static final ItemsListKey blocks;
  public static final ItemsListKey depends;
  public static final ModelKey<String> duplicateOf;
  public static final CommentListModelKey comments;
  public static final DummyBugKey dummy;
  public static final ModelKey<List<AttachmentInfo>> attachments;
  public static final ModelKey<BugGroupInfo> groups;
  public static final ModelKey<List<ModelKey<?>>> customFields;
  public static final BugzillaVoteKeys voteKeys;
  public static final ModelKey<List<String>> seeAlso;

  public static final List<SimpleModelKey<?, String>> allTextKeys;

  private static final Map<String, ModelKey<ItemKey>> myEnumFieldTypeNames;
  private static final KeysBuilder myKeysBuilder;

  static {
    final KeysBuilder builder = new KeysBuilder();

    id = builder.addID();

    product   = builder.addComboBox(SingleEnumAttribute.PRODUCT, false);
    component = builder.addProductDependent(product, SingleEnumAttribute.COMPONENT);
    version   = builder.addProductDependent(product, SingleEnumAttribute.VERSION);
    milestone = builder.addProductDependent(product, SingleEnumAttribute.TARGET_MILESTONE);

    builder.addKey(ProductDependenciesKey.KEY, null);

    platform   = builder.addComboBox(SingleEnumAttribute.PLATFORM, false);
    os         = builder.addComboBox(SingleEnumAttribute.OS, false);
    priority   = builder.addComboBox(SingleEnumAttribute.PRIORITY, false);
    severity   = builder.addComboBox(SingleEnumAttribute.SEVERITY, false);
    resolution = builder.addResolutionComboBox();
    status     = builder.addStatus();

    assignedTo = builder.addComboBox(SingleEnumAttribute.ASSIGNED_TO, true);
    qaContact  = builder.addComboBox(SingleEnumAttribute.QA_CONTACT, true);
    reporter   = builder.addComboBox(SingleEnumAttribute.REPORTER, false);

    cc = builder.addKey(new CCListModelKey(), Bug.attrCcList);

    comments    = builder.addKey(new CommentListModelKey(new CommentsFactory()), null);
    attachments = builder.addKey(AttachmentsModelKey.INSTANCE, null);
    builder.addKey(FlagsModelKey.MODEL_KEY, null);

    summary          = builder.addStyledTextDocumentKey(BugzillaAttribute.SHORT_DESCRIPTION, Bug.attrSummary);
    statusWhiteboard = builder.addTextDocumentKey(BugzillaAttribute.STATUS_WHITEBOARD, Bug.attrStatusWhiteboard);
    alias            = builder.addTextDocumentKey(BugzillaAttribute.ALIAS, Bug.attrAlias);
    url              = builder.addTextDocumentKey(BugzillaAttribute.BUG_URL, Bug.attrUrl);

    keywords = builder.createKeywordsKey();

    modificationTime = builder.addKey(
      ValueKey.createDateValue(Bug.attrModificationTime, false,
        BugzillaUtil.getDisplayableFieldName(BugzillaAttribute.MODIFICATION_TIMESTAMP),
        ModelMergePolicy.COPY_VALUES), Bug.attrModificationTime);
    creationTime = builder.addKey(
      ValueKey.createDateValue(Bug.attrCreationTime, false,
        BugzillaUtil.getDisplayableFieldName(BugzillaAttribute.CREATION_TIMESTAMP),
        ModelMergePolicy.COPY_VALUES), Bug.attrCreationTime);

    blocks        = builder.addBugListValue(BugzillaAttribute.BLOCKS, Bug.attrBlocks);
    depends       = builder.addBugListValue(BugzillaAttribute.BLOCKED_BY, Bug.attrBlockedBy);
    duplicateOf   = builder.addKey(
      new BugReferenceModelKey(Bug.attrDuplicateOf, BugzillaUtil.getDisplayableFieldName(BugzillaAttribute.DUPLICATE_OF), null),
      Bug.attrDuplicateOf);

    dummy = builder.addKey(new DummyBugKey(), null);

    deadline      = builder.addDate(BugzillaAttribute.DEADLINE, Bug.attrDeadline);
    estimatedTime = builder.addDecimalDocumentKey(BugzillaAttribute.ESTIMATED_TIME, Bug.attrEstimatedTime, true);
    workedTime    = builder.addDecimalDocumentKey(BugzillaAttribute.ACTUAL_TIME, Bug.attrActualTime, ModelMergePolicy.COPY_VALUES, false);
    totalWorkTime = builder.addDecimalDocumentKey(BugzillaAttribute.ACTUAL_TIME, Bug.attrTotalActualTime, ModelMergePolicy.COPY_VALUES, true);
    remainingTime = builder.addDecimalDocumentKey(BugzillaAttribute.REMAINING_TIME, Bug.attrRemainingTime, true);

    final ModelKey<List<ItemKey>> voterList   = builder.addVotersListKey();
    final ModelKey<List<Integer>> votesValues = builder.addVotesValuesList();
    final ModelKey<Integer> votes             = builder.addVoteValue(VotesLink.votes, "Votes");
    final ModelKey<Integer> ourVote           = builder.addMyVotesValue(VotesLink.votesByUser, "Votes(my)");
    final ModelKey<Integer> votesPerBug       = builder.addVoteProductValue(VotesLink.productMaxVotesPerBug, "Votes per bug");
    final ModelKey<Integer> votesPerProduct   = builder.addVoteProductValue(VotesLink.productMaxVotes, "Votes per product");
    voteKeys = new BugzillaVoteKeys(voterList, votesValues, ourVote, votes, votesPerBug, votesPerProduct);

    seeAlso = builder.addKey(new SeeAlsoModelKey(), Bug.attrSeeAlso);

    groups = builder.addKey(createGroupsKey(), Group.CONTAINING_GROUPS);
    customFields = builder.addKey(createCustomFieldsKey(), null);
    myKeysBuilder = builder;

    myEnumFieldTypeNames = Collections.unmodifiableMap(createEnumFieldTypeNames());
    allTextKeys = Collections.unmodifiableList(Arrays.<SimpleModelKey<?, String>>asList(summary, alias, statusWhiteboard, url));
  }

  public static Map<String, ModelKey<ItemKey>> getEnumFieldTypeNames() {
    return myEnumFieldTypeNames;
  }

  public static List<ModelKey<?>> getKeys() {
    return myKeysBuilder.getKeys();
  }

  public static Map<DBAttribute, ModelKey<?>> getKeyMap() {
    return myKeysBuilder.getKeyMap();
  }

  private static Map<String, ModelKey<ItemKey>> createEnumFieldTypeNames() {
    Map<String, ModelKey<ItemKey>> map = Collections15.hashMap();
    map.put("product", product);
    map.put("component", component);
    map.put("platform", platform);
    map.put("operating_system", os);
    map.put("status", status);
    map.put("resolution", resolution);
    map.put("severity", severity);
    map.put("priority", priority);
    return map;
  }

  private static ModelKey<List<ModelKey<?>>> createCustomFieldsKey() {
    BaseKeyBuilder<List<ModelKey<?>>> builder = BaseKeyBuilder.create();
    builder.setDisplayName("Custom Fields");
    builder.setMergePolicy(ModelMergePolicy.IGNORE);
    builder.setName("customFields");
    builder.setIO(new BugzillaCustomFieldsIO());
    return builder.getKey();
  }

  private static ModelKey<BugGroupInfo> createGroupsKey() {
    BaseKeyBuilder<BugGroupInfo> builder = BaseKeyBuilder.create();
    builder.setName("groups");
    builder.setDisplayName("Groups");
    builder.setMergePolicy(ModelMergePolicy.MANUAL);
    builder.setIO(new GroupsIO());
    builder.setExport(BugGroupInfo.EXPORT);
    builder.setAccessor(new BaseModelKey.SimpleDataAccessor<BugGroupInfo>("groups") {
      @Override
      protected boolean isExistingValue(BugGroupInfo value) {
        return value != null && value != BugGroupInfo.GROUPS_UNKNOWN;
      }

      @Override
      protected Object getCanonicalValueForComparison(@Nullable BugGroupInfo value) {
        return value == BugGroupInfo.GROUPS_UNKNOWN ? null : value;
      }
    });
    builder.setPromotionPolicy(DataPromotionPolicy.ALWAYS);
    return builder.getKey();
  }

  private static String keywordsToString(@NotNull Collection<ItemKey> keywords) {
    if (keywords.isEmpty()) return "";
    StringBuilder s = new StringBuilder();
    for(ItemKey key: keywords) {
      if (key != null) s.append(key.getId());
    }
    return s.toString();
  }

  static ColumnsBuilder createColumns() {
    final ColumnsBuilder columns = ColumnsBuilder.create(id, dummy);

    columns.addValueColumnNoDummy(id, 6);
    columns.addValueColumn(summary, 60);
    columns.addItemColumn(status, 10);
    columns.addItemColumn(priority, 4);
    columns.addItemColumn(milestone, 10);

    columns.addItemColumn(product);
    columns.addItemColumn(component);
    columns.addItemColumn(platform);
    columns.addItemColumn(os);
    columns.addItemColumn(version);
    columns.addItemColumn(severity);
    columns.addItemColumn(resolution);
    columns.addItemColumn(qaContact, 15);
    columns.addItemColumn(assignedTo, 15);

    columns.addValueColumn(statusWhiteboard, 15);
    columns.addValueColumn(modificationTime, 15);
    columns.addValueColumn(creationTime, 15);
    columns.addValueColumn(url, 15);
    columns.addValueColumn(alias, 6);
    columns.addValueColumn(reporter, 15);
    columns.addValueColumn(duplicateOf, 6);

    // todo verify B-1-7
    columns.addValueColumn(voteKeys.votes, 5);
    columns.addValueColumn(voteKeys.votesMy, 5);

    columns.addCollectionColumn(depends, 10);
    columns.addCollectionColumn(blocks, 10);

    // lists are considered not null
    final Comparator<Collection<ItemKey>> keywordsComparator = new Comparator<Collection<ItemKey>>() {
      public int compare(Collection<ItemKey> o1, Collection<ItemKey> o2) {
        return keywordsToString(o1).compareToIgnoreCase(keywordsToString(o2));
      }
    };
    columns.addComparableColumn(keywords, keywordsComparator, 15);

    columns.addCollectionColumn(new SeeAlsoModelKey(), 25);

    columns.addCollectionColumn(attachments, 2,
      "<html><body><img alt=\"Attachments\" src=\"" + Icons.ATTACHMENT.getURL() + "\">" + (!Env.isMac() ? "&nbsp;" : "") + "</body></html>");
    columns.addCollectionColumn(comments, 11, "Comments #");
    columns.addValueColumn(estimatedTime, 5);
//    columns.addValueColumn(workedTime, 5); // debugging only
    columns.addValueColumn(totalWorkTime, 5);
    columns.addValueColumn(remainingTime, 5);
    columns.addValueColumn(deadline, 8);

    columns.addValueColumn(groups, 10);
    return columns;
  }
  
  private BugzillaKeys() {}
}