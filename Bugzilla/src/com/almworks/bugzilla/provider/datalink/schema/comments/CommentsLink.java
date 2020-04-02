package com.almworks.bugzilla.provider.datalink.schema.comments;

import com.almworks.api.constraint.FieldSubstringsConstraint;
import com.almworks.api.constraint.OneFieldConstraint;
import com.almworks.api.engine.util.CommentsLinkHelper;
import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.bugzilla.integration.data.Comment;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.*;
import com.almworks.bugzilla.provider.datalink.*;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.sync.util.DiscardAll;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.items.util.DBNamespace;
import com.almworks.itemsync.MergeOperationsManager;
import com.almworks.util.Pair;
import com.almworks.util.bool.BoolExpr;
import com.almworks.util.text.TextUtil;
import org.almworks.util.Collections15;
import org.almworks.util.StringUtil;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * @author sereda
 */
public class CommentsLink implements DataLink, RemoteSearchable {
  public static final DBNamespace NS = BugzillaProvider.NS.subNs("comment");
  public static final DBItemType typeComment = NS.type();

  public static final DBAttribute<Long> attrMaster = NS.master("bug");
  public static final DBAttribute<Long> attrAuthor = NS.link("author", "Author", false);
  public static final DBAttribute<Date> attrDate = NS.date("date", "Date", false);
  // NB: Name is important for queries created in older versions to work
  public static final DBAttribute<String> attrText = NS.string("text", "comments", true);
  public static final DBAttribute<Integer> attrIndex = NS.integer("index", "Index", false);
  public static final DBAttribute<BigDecimal> attrWorkTime = NS.decimal("workTime", "Work Time", true);
  public static final DBAttribute<Boolean> attrPrivate = NS.bool("private", "Private?", true);
  public static final BoolExpr<DP> IS_COMMENT = DPEqualsIdentified.create(DBAttribute.TYPE, typeComment);

  static {
    SyncSchema.setDecimalScale(2, attrWorkTime);
  }

  @NotNull
  public BooleanChart.Element buildBooleanChartElement(@NotNull CommentsContainAllWords constraint, boolean negated) {
    List<String> words = constraint.getWords();
    String searchTerm = StringUtil.implode(words, " ");
    BooleanChartElementType type =
      negated ? BooleanChartElementType.NONE_OF_WORDS : BooleanChartElementType.CONTAINS_ALL;
    return BooleanChart.createElement(BugzillaHTMLConstants.BOOLEAN_CHART_COMMENTS_SPECIAL, type, searchTerm);
  }

  public void updateRevision(PrivateMetadata pm, ItemVersionCreator bug, BugInfo bugInfo, @NotNull BugzillaContext context) {
    new UpdateComments(context, bug, bugInfo.getOrderedComments(), bugInfo.getStringID(),
      bugInfo.getUserSeesPrivateComments()).perform();
  }

  public void buildUploadInfo(ItemUploader.UploadPrepare prepare, ItemDiff bug, BugInfoForUpload info) {
    prepare.addToUpload(bug.getNewerVersion().getSlaves(CommentsLink.attrMaster));
  }

  public String detectFailedUpdate(BugInfo newInfo, BugInfoForUpload updateInfo, PrivateMetadata privateMetadata) {
    List<BugInfoForUpload.CommentForUpload> requestedComments = updateInfo.getComments();
    if (requestedComments.size() == 0)
      return null;

    boolean hadAttachments = updateInfo.getAttachments().length > 0;
    boolean hadDuplicate = updateInfo.getNewValues().getScalarValue(BugzillaAttribute.DUPLICATE_OF, null) != null;
    if (hadAttachments || hadDuplicate)
      return null;

    Set<Integer> requestedHashes = Collections15.hashSet();
    for (BugInfoForUpload.CommentForUpload requestedComment : requestedComments)
      requestedHashes.add(CommentsLinkHelper.getCommentHash(requestedComment.getComment()));
    Comment[] comments = newInfo.getOrderedComments();
    for (int i = comments.length - 1; i >= 0; i--) {
      requestedHashes.remove(CommentsLinkHelper.getCommentHash(comments[i].getText()));
      if (requestedHashes.size() == 0)
        break;
    }
    return requestedHashes.size() == 0 ? null : "comment";
  }

  @Override
  public void initializePrototype(ItemVersionCreator prototype, PrivateMetadata pm) {
  }

  public String toString() {
    return "link[comments]";
  }

  @Override
  @Nullable
  public BooleanChart.Element buildBooleanChartElement(
    OneFieldConstraint constraint, boolean negated, DBReader reader, BugzillaConnection connection)
  {
    BooleanChartElementType type = LinkUtil.getDefaultBCElementType(constraint, negated);
    String value;
    if (constraint instanceof FieldSubstringsConstraint) {
      value = TextUtil.separate(((FieldSubstringsConstraint) constraint).getSubstrings(), " ");
    } else {
      assert false : constraint;
      return null;
    }
    Pair<String, BooleanChartElementType> fixedPair = LinkUtil.fixEmptyBooleanChartCondition(value, type);
    if (fixedPair != null) {
      value = fixedPair.getFirst();
      type = fixedPair.getSecond();
    }
    return BooleanChart.createElement(BugzillaHTMLConstants.BOOLEAN_CHART_COMMENTS_SPECIAL, type, value);
  }

  public static void registerMergers(MergeOperationsManager mm) {
    mm.addMergeOperation(new DiscardAll(), typeComment);
  }

  public static boolean isFinal(ItemVersion comment) {
    ItemVersion base = SyncUtils.readBaseIfExists(comment.getReader(), comment.getItem());
    return base == null || !base.isInvisible();
  }
}
