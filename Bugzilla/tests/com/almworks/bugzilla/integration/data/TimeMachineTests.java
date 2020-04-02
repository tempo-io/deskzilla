package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Collections15;

import java.util.Date;
import java.util.List;

public class TimeMachineTests extends BaseTestCase {
  private Date myNow;
  private static final int DAY = 86400000;

  protected void setUp() throws Exception {
    super.setUp();
    myNow = getRawTime();
  }

  protected void tearDown() throws Exception {
    myNow = null;
    super.tearDown();
  }

  public void testCommentsIncorporation() {
    List<ChangeSet> changes = Collections15.arrayList();
    Date date1 = time(0);
    Date date2 = time(DAY);
    changes.add(createChangeSet(date1, "pupkin", BugzillaAttribute.KEYWORDS, "X", "Y"));
    changes.add(createChangeSet(date2, "gates", BugzillaAttribute.KEYWORDS, "Z X", "X"));

    List<Comment> comments = Collections15.arrayList();
    comments.add(createComment(date1, "pupkin", "keywords should be Y"));
    comments.add(createComment(time(DAY * 10), "lord", "let this artifact be"));

    List<ChangeSet> result =
      TimeMachine.incorporateComments(changes, comments, BugzillaDateUtil.format(time(DAY * 10)), null);

    assertEquals(2, result.size());
    checkChange(result.get(0), date1, "pupkin", 1, BugzillaAttribute.KEYWORDS, "X", "Y", "keywords should be Y");
    checkChange(result.get(1), date2, "gates", 1, BugzillaAttribute.KEYWORDS, "Z X", "X", null);
  }

  public void testRebuildingSplitAttribute() {
    BugInfo info = getOneValueInfo(100, BugzillaAttribute.SHORT_DESCRIPTION,
      "This bug once had a very very long summary. Now it's gone.");
    List<ChangeSet> activity = Collections15.arrayList();

    activity.add(createChangeSet(time(DAY * 1), "mrx", BugzillaAttribute.SHORT_DESCRIPTION,
      "This bug once had a very very long summary", "This bug once had a very very long summary. Now it's gone."));

    activity.add(createChangeSet(time(DAY * 2), "mrx", BugzillaAttribute.SHORT_DESCRIPTION,
      "[TEST] This is a bug submitted by application - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long -",
      "This bug once had a very very long summary", BugzillaAttribute.SHORT_DESCRIPTION,
      "summary long is long - summary long is long - summary long is long - summary long is long x", ""));

    activity.add(createChangeSet(time(DAY * 3), "mrx", BugzillaAttribute.SHORT_DESCRIPTION,
      "[TEST] This is a bug submitted by application - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long -",
      "[TEST] This is a bug submitted by application - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long -",
      BugzillaAttribute.SHORT_DESCRIPTION,
      "summary long is long - summary long is long - summary long is long - summary long is long",
      "summary long is long - summary long is long - summary long is long - summary long is long x"));

    activity.add(createChangeSet(time(DAY * 4), "mrx", BugzillaAttribute.SHORT_DESCRIPTION,
      "[TEST] This is a bug submitted by application",
      "[TEST] This is a bug submitted by application - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long -",
      BugzillaAttribute.SHORT_DESCRIPTION, "",
      "summary long is long - summary long is long - summary long is long - summary long is long"));

    List<BugInfo> result = TimeMachine.recreateHistory(info, activity);
    assertEquals(5, result.size());
    checkDescription(result.get(0), "[TEST] This is a bug submitted by application");
    checkDescription(result.get(1),
      "[TEST] This is a bug submitted by application - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long -summary long is long - summary long is long - summary long is long - summary long is long");
    checkDescription(result.get(2),
      "[TEST] This is a bug submitted by application - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long - summary long is long -summary long is long - summary long is long - summary long is long - summary long is long x");
    checkDescription(result.get(3), "This bug once had a very very long summary");
    checkDescription(result.get(4), "This bug once had a very very long summary. Now it's gone.");
  }


  public void testIncorporatingCommentsWithoutFirstComment() {
    List<ChangeSet> changes = Collections15.arrayList();
    changes.add(createChangeSet(time(0), "pupkin", BugzillaAttribute.KEYWORDS, "X", "Y"));
    changes.add(createChangeSet(time(DAY), "gates", BugzillaAttribute.KEYWORDS, "Z X", "X"));

    List<Comment> comments = Collections15.arrayList();
    comments.add(createComment(time(0), "pupkin", "keywords should be Y"));

    List<ChangeSet> result = TimeMachine.incorporateComments(changes, comments, BugzillaDateUtil.format(time(0)), null);

    assertEquals(2, result.size());
    checkChange(result.get(0), time(0), "pupkin", 1, BugzillaAttribute.KEYWORDS, "X", "Y", "keywords should be Y");
    checkChange(result.get(1), time(DAY), "gates", 1, BugzillaAttribute.KEYWORDS, "Z X", "X", null);
  }

  private void checkDescription(BugInfo info, String description) {
    assertEquals(description, info.getValues().getMandatoryScalarValue(BugzillaAttribute.SHORT_DESCRIPTION));
  }

  private BugInfo getOneValueInfo(int bugID, BugzillaAttribute attribute, String value) {
    BugInfo info = new BugInfo(null);
    info.getValues().put(BugzillaAttribute.ID, Integer.toString(bugID));
    info.getValues().put(attribute, value);
    return info;
  }


  private void checkChange(ChangeSet changeSet, Date when, String who, int changeCount, BugzillaAttribute attribute,
    String removed, String added, String commentText)
  {
    assertEquals(who, changeSet.getWho().getEmailId());
    assertEquals(when, changeSet.getWhen());
    assertEquals(changeCount, changeSet.getChanges().size());
    if (changeSet.getChanges().size() > 0) {
      ChangeSet.Change change = changeSet.getChanges().get(0);
      assertEquals(attribute, change.what);
      assertEquals(removed, change.getRemoved());
      assertEquals(added, change.getAdded());
    } else {
      assertEquals(null, attribute);
      assertEquals(null, removed);
      assertEquals(null, added);
    }
    Comment comment = changeSet.getComment();
    if (comment != null) {
      assertEquals(who, comment.getWho().getEmailId());
      assertEquals(when, comment.getWhenDate());
      assertEquals(commentText, comment.getText());
    } else {
      assertEquals(null, commentText);
    }
  }

  private Date time(long past) {
    if (past <= 0)
      return myNow;
    else
      return new Date(myNow.getTime() - past);
  }

  private Comment createComment(Date when, String who, String text) {
    return new Comment(BugzillaUser.shortEmailName(who, null, null), BugzillaDateUtil.format(when), text, null, null, null);
  }

  private ChangeSet createChangeSet(Date when, String who, BugzillaAttribute what, String removed, String added) {
    ChangeSet changeSetOne = new ChangeSet(BugzillaUser.longEmailName(who, null), when);
    changeSetOne.addChange(new ChangeSet.Change(what, removed, added));
    return changeSetOne;
  }

  private ChangeSet createChangeSet(Date when, String who, BugzillaAttribute what1, String removed1, String added1,
    BugzillaAttribute what2, String removed2, String added2)
  {
    ChangeSet changeSetOne = new ChangeSet(BugzillaUser.longEmailName(who, null), when);
    changeSetOne.addChange(new ChangeSet.Change(what1, removed1, added1));
    changeSetOne.addChange(new ChangeSet.Change(what2, removed2, added2));
    return changeSetOne;
  }

  private Date getRawTime() {
    long t = System.currentTimeMillis();
    return new Date(t - (t % 60000));
  }
}
