package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.bugzilla.provider.KeywordsTool;
import org.almworks.util.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * :todoc:
 *
 * @author sereda
 */
public class TimeMachine {
  private static final int MINUTE = 60000;

  public static List<BugInfo> recreateHistory(BugInfo lastRevision, List<ChangeSet> activity) {
    Integer id = lastRevision.getID();
    activity = incorporateComments(activity, lastRevision.copyComments(),
      lastRevision.getValues().getScalarValue(BugzillaAttribute.CREATION_TIMESTAMP, ""),
      lastRevision.getDefaultTimezone());
    List<BugInfo> result = Collections15.arrayList();
    BugInfo info = lastRevision.copy();
    long mtime = lastRevision.getMTime();
    result.add(info);
    boolean last = true;
    for (ChangeSet changeSet : activity) {
      // replacing mtime and changer on the already added info - due to backwards history rollout
      BugzillaUser who = changeSet.getWho();
      replaceChangeAuthor(info.getValues(), who != null ? who.getEmailId() : null);
      if (!last) {
        replaceModificationTime(info.getValues(), BugzillaDateUtil.format(changeSet.getWhen()));
      } else {
        last = false;
        if (Math.abs(changeSet.getWhen().getTime() - mtime) >= MINUTE) {
          // this is normal - because we can do "empty update" on bugzilla page, which will change mtime
          Log.debug(
            "mtime of last change is not within a minute of bug mtime [" + changeSet.getWhen().getTime() + "][" +
              mtime + "] bug[" + id + "]");
        }
      }
      info = info.copy();
      BugzillaValues values = info.getValues();
      List<ChangeSet.Change> changes = changeSet.getChanges();
      for (int j = changes.size() - 1; j >= 0; j--) {
        ChangeSet.Change change = changes.get(j);
        BugzillaAttribute attribute = change.what;
        assert attribute != null;
        String added = change.getAdded();
        String removed = change.getRemoved();
        if (attribute.isTuple())
          rollbackTupleChange(values, attribute, removed, added, id);
        else
          rollbackScalarChange(values, attribute, removed, added, id);
      }
      Comment addedComment = changeSet.getComment();
      if (addedComment != null)
        rollbackComment(info, addedComment);
      result.add(info);
    }
    // augment first revision
    replaceChangeAuthor(info.getValues(), info.getValues().getScalarValue(BugzillaAttribute.REPORTER, null));
    if (!last) {
      String creationTime = info.getValues().getScalarValue(BugzillaAttribute.CREATION_TIMESTAMP, null);
      // todo if first comment time is more precise, use it instead of creation time
      //long firstCommentTime = (info.getComments().length > 0) ? info.getComments()[0].getWhenDate().getTime() : 0;
      replaceModificationTime(info.getValues(), creationTime);
    }
    Collections.reverse(result);
    return result;
  }

  /**
   * Returns new changeset with the comments as changes
   */
  static List<ChangeSet> incorporateComments(List<ChangeSet> activity, List<Comment> commentsList, String creationDate,
    TimeZone defaultTimezone)
  {
    // assert activity is sorted in reverse time order
/*
    if (sourceComments == null || sourceComments.size() <= 1) {
      // first comment is always description - WRONG!!!
      return activity;
    }
*/
    if (commentsList == null || commentsList.size() == 0) {
      return activity;
    }
    List<ChangeSet> result = Collections15.arrayList();
    List<Comment> comments = Collections15.arrayList(commentsList);
    Collections.sort(comments, Comment.REVERSE_TIME_ORDER);
    Iterator<ChangeSet> ichanges = activity.iterator();
    Iterator<Comment> icomments = comments.iterator();
    ChangeSet sourceChangeSet = ichanges.hasNext() ? ichanges.next() : null;
    while (icomments.hasNext()) {
      Comment comment = icomments.next();
      long commentTime = comment.getWhenDate().getTime();
      BugzillaUser author = comment.getWho();
      ChangeSet newChange = null;
      while (sourceChangeSet != null) {
        long changeTime = sourceChangeSet.getWhen().getTime();
        if (Math.abs(changeTime - commentTime) < (MINUTE / 2) && Util.equals(sourceChangeSet.getWho(), author)) {
          // merge change & comment
          newChange = new ChangeSet(sourceChangeSet, comment);
          sourceChangeSet = ichanges.hasNext() ? ichanges.next() : null;
          break;
        }
        if (changeTime <= commentTime) {
          break;
        }
        result.add(sourceChangeSet);
        sourceChangeSet = ichanges.hasNext() ? ichanges.next() : null;
      }
      if (newChange == null) {
        if (!icomments.hasNext()) {
          // first comment is a "description" only if it has roughly the same time as the creation time.
          if (creationDate != null && creationDate.length() > 0) {
            Date date = BugzillaDateUtil.parse(creationDate, defaultTimezone);
            if (date != null && Math.abs(date.getTime() - commentTime) < (MINUTE / 2)) {
              break;
            }
          }
        }
        newChange = new ChangeSet(comment);
      }
      result.add(newChange);
    }
    if (sourceChangeSet != null)
      result.add(sourceChangeSet);
    while (ichanges.hasNext())
      result.add(ichanges.next());
    return result;
  }

  private static boolean contains(String element, String[] array) {
    assert element != null;
    for (int i = 0; i < array.length; i++) {
      if (element.equals(array[i]))
        return true;
    }
    return false;
  }

  private static void inconsistentHistory(String message, Integer bugID) {
    inconsistentHistory(message, bugID, null);
  }

  private static void inconsistentHistory(String message, Integer bugID, BugzillaAttribute attribute) {
    StringBuffer buf = new StringBuffer();
    buf.append("inconsistent history [").append(bugID).append("]");
    if (attribute != null)
      buf.append("[").append(attribute).append("]");
    buf.append(' ').append(message);
    Log.warn(buf.toString());
  }

  private static void replaceChangeAuthor(BugzillaValues values, String fullEmail) {
    if (fullEmail == null || fullEmail.length() == 0) return;
    values.clear(BugzillaAttribute.MODIFICATION_AUTHOR);
    values.put(BugzillaAttribute.MODIFICATION_AUTHOR, fullEmail);
  }

  private static void replaceModificationTime(BugzillaValues values, String date) {
    if (date == null || date.length() == 0)
      return;
    values.clear(BugzillaAttribute.MODIFICATION_TIMESTAMP);
    values.clear(BugzillaAttribute.DELTA_TS);
    values.put(BugzillaAttribute.MODIFICATION_TIMESTAMP, date);
  }

  private static void rollbackComment(BugInfo info, Comment addedComment) {
    boolean removed = info.removeComment(addedComment);
    if (!removed) {
      inconsistentHistory("cannot remove comment [" + addedComment + "]", info.getID());
    }

    String text = addedComment.getText();
    String id = info.getLastAttachmentId();
    String searchString = "(id=" + id + ")";
    int k = text.indexOf(searchString);
    if (k < 0 || k > 50)
      return;

    boolean attachComment = false;
    for (int i = 0; i < k; i++) {
      char c = text.charAt(i);
      if (Character.isWhitespace(c))
        continue;
      if (c == '>')
        return;
      if (Character.isLetterOrDigit(c)) {
        attachComment = true;
        break;
      }
    }

    if (attachComment)
      info.removeLastAttachment();
  }

  private static void rollbackScalarChange(BugzillaValues values, BugzillaAttribute attribute, String removed,
    String added, Integer id)
  {

    if (attribute == BugzillaAttribute.KEYWORDS) {
      rollbackKeywords(values, removed, added, id);
    } else if (attribute.isLongBreakableText()) {
      rollbackScalarLongBreakableText(values, attribute, removed, added, id);
    } else if (attribute.isDecimal()) {
      rollbackScalarDecimalText(values, attribute, removed, added, id);
    } else {
      rollbackSimpleScalarChange(values, attribute, removed, added, id);
    }
  }

  private static void rollbackKeywords(BugzillaValues values, String removed, String added, Integer id) {
    String value = values.getScalarValue(BugzillaAttribute.KEYWORDS, "");
    SortedMap<String, String> keywords = Collections15.treeMap(KeywordsTool.splitString(value));
    SortedMap<String, String> addedKeywords = KeywordsTool.splitString(added);
    SortedMap<String, String> removedKeywords = KeywordsTool.splitString(removed);
    for (String kwd : addedKeywords.keySet()) {
      if (keywords.remove(kwd) == null) {
        inconsistentHistory("added keyword " + addedKeywords.get(kwd) + " value [" + value + "]", id,
          BugzillaAttribute.KEYWORDS);
      }
    }
    keywords.putAll(removedKeywords);
    String result = KeywordsTool.composeString(keywords);
    values.clear(BugzillaAttribute.KEYWORDS);
    values.put(BugzillaAttribute.KEYWORDS, result);
  }

  private static void rollbackScalarDecimalText(BugzillaValues values, BugzillaAttribute attribute, String removed,
    String added, Integer id)
  {

    String value = values.getScalarValue(attribute, "");
    if (value.length() == 0 || added.length() == 0) {
      rollbackSimpleScalarChange(values, attribute, removed, added, id);
      return;
    }
    try {
      BigDecimal decValue = new BigDecimal(value);
      BigDecimal decAdded = new BigDecimal(added);
      if (attribute.isCumulativelyReported() && removed.length() == 0) {
        // "added" means added ariphmetically to the value.
        if (decAdded.compareTo(decValue) > 0) {
          inconsistentHistory("added " + decAdded + "; value " + decValue, id, attribute);
          removed = "0.0";
        } else {
          removed = decValue.subtract(decAdded).toString();
        }
      } else {
        if (decValue.compareTo(decAdded) != 0) {
          inconsistentHistory("added " + decAdded + "; value " + decValue, id, attribute);
        }
      }
    } catch (NumberFormatException e) {
      // falling back to simple
      inconsistentHistory("cannot understand as decimal [" + value + "][" + added + "]", id, attribute);
      rollbackSimpleScalarChange(values, attribute, removed, added, id);
      return;
    }
    values.clear(attribute);
    if (removed.length() > 0)
      values.put(attribute, removed);
  }

  private static void rollbackScalarLongBreakableText(BugzillaValues values, BugzillaAttribute attribute,
    String removed, String added, Integer id)
  {

    StringBuffer value = new StringBuffer(values.getScalarValue(attribute, ""));
    int addedLength = added.length();
    int k = addedLength > 0 ? value.lastIndexOf(added) : value.length();
    if (k == -1) {
      inconsistentHistory("added [" + added + "]; value [" + value + "]", id, attribute);
      value.delete(0, value.length());
      k = 0;
    } else {
      if (addedLength > 0)
        value.delete(k, k + addedLength);
    }

    if (removed.length() > 0)
      value.insert(k, removed);

    values.clear(attribute);
    String s = value.toString().trim();
    if (s.length() > 0)
      values.put(attribute, s);
  }

  private static void rollbackSimpleScalarChange(BugzillaValues values, BugzillaAttribute attribute, String removed,
    String added, Integer id)
  {

    String value = values.getScalarValue(attribute, "");
    if (!value.equals(added)) {
      inconsistentHistory("added " + added + "; value " + value, id, attribute);
    }
    values.clear(attribute);
    if (removed.length() > 0)
      values.put(attribute, removed);
  }

  private static void rollbackTupleChange(BugzillaValues values, BugzillaAttribute attribute, String removed,
    String added, Integer id)
  {

    if (added.length() > 0) {
      String[] addedScalars = added.split("\\s*,\\s*");
      List<String> scalars = values.getTupleValues(attribute);
      values.clear(attribute);
      boolean found = false;
      for (String scalar : scalars) {
        if (!contains(scalar, addedScalars))
          values.put(attribute, scalar);
        else
          found = true;
      }
      if (!found)
        inconsistentHistory("cannot find value " + added, id, attribute);
    }
    if (removed.length() > 0) {
      String[] removedScalars = removed.split("\\s*,\\s*");
      for (int i = 0; i < removedScalars.length; i++) {
        values.put(attribute, removedScalars[i]);
      }
    }
  }
}
