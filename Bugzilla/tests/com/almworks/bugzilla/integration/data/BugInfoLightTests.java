package com.almworks.bugzilla.integration.data;

import com.almworks.util.tests.BaseTestCase;
import com.almworks.util.tests.CollectionsCompare;
import org.almworks.util.Collections15;
import org.almworks.util.Util;

import java.util.List;

/**
 * @author dyoma
 */
public class BugInfoLightTests extends BaseTestCase {
//    yyyy-MM-dd HH:mm
  private static final String DATE1 = "2005-01-01 10:00";
  private static final String DATE2 = "2005-01-01 10:01";
  private static final String DATE3 = "2005-01-01 10:02";
  private final BugInfo myInfo = new BugInfo(null);
  private static final String[] EXPECTED_ORDER = new String[] {"description", "comment1", "comment2"};
  private final CollectionsCompare CHECK = new CollectionsCompare();
  public static final BugzillaUser WHOEVER = BugzillaUser.shortEmailName("whoever", null, null);
  public static final BugzillaUser WHOEVER2 = BugzillaUser.shortEmailName("whoever2", null, null);
  public static final BugzillaUser UNK = BugzillaUser.shortEmailName("unk", null, null);

  public void testCommentsOld() {
    addComment(DATE1, 0);
    addComment(DATE2, 1);
    addComment(DATE3, 2);
    checkOrder();
  }

  public void testAllSameDate() {
    addComment(DATE1, 0);
    addComment(DATE1, 1);
    addComment(DATE1, 2);
    checkOrder();
  }

  public void testTailSameDate() {
    addComment(DATE1, 0);
    addComment(DATE2, 1);
    addComment(DATE2, 2);
    checkOrder();
  }

  public void testReverse() {
    addComment(DATE3, 2);
    addComment(DATE2, 1);
    addComment(DATE1, 0);
    checkOrder();
  }

  public void testDescriptionFirstReverse() {
    addComment(DATE1, 0);
    addComment(DATE3, 2);
    addComment(DATE2, 1);
    checkOrder();
  }

  public void testHeadSameDate() {
    addComment(DATE1, 0);
    addComment(DATE1, 1);
    addComment(DATE2, 2);
    checkOrder();
  }

  public void testHeadSameDateReverse() {
    addComment(DATE1, 0);
    addComment(DATE2, 2);
    addComment(DATE1, 1);
    checkOrder();
  }

  public void testAttachmentsFound() {
    myInfo.addComment(new Comment(WHOEVER, DATE1, "Created an attachment (id=3)\n" +
      "description ca kca ca mca kcam kca camk cak cdak \n" + "\n" + "optional comment gere", null, null, null));
    BugInfo.Attachment[] attachments = Util.NN(myInfo.fetchAttachments(), BugInfo.Attachment.EMPTY_ARRAY);
    assertEquals(1, attachments.length);
    assertEquals("3", attachments[0].id);
    assertEquals(DATE1, attachments[0].date);
    assertEquals("description ca kca ca mca kcam kca camk cak cdak ", attachments[0].description);
  }

  public void testSameAttachmentsCoalesced() {
    myInfo.addComment(new Comment(WHOEVER, DATE1, "Created an attachment (id=3)\n" +
      "description ca kca ca mca kcam kca camk cak cdak \n" + "\n" + "optional comment gere", null, null, null));
    myInfo.addComment(new Comment(WHOEVER2, DATE2, "Modified an attachment (id=3)\n" +
      "description ca kca ca mca kcam scddc dcdcd \n" + "\n" + "optional comment gere", null, null, null));
    BugInfo.Attachment[] attachments = Util.NN(myInfo.fetchAttachments(), BugInfo.Attachment.EMPTY_ARRAY);
    assertEquals(1, attachments.length);
    assertEquals("3", attachments[0].id);
    assertEquals(DATE1, attachments[0].date);
    assertEquals("description ca kca ca mca kcam kca camk cak cdak ", attachments[0].description);
  }

  private void addComment(String date, int index) {
    myInfo.addComment(new Comment(UNK, date, EXPECTED_ORDER[index], null, null, null));
  }

  private void checkOrder() {
    List<String> texts = Collections15.arrayList();
    Comment[] comments = myInfo.getOrderedComments();
    for (Comment comment : comments)
      texts.add(comment.getText());
    CHECK.order(EXPECTED_ORDER, texts);
  }
}
