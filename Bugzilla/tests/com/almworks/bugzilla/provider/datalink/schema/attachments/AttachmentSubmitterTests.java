package com.almworks.bugzilla.provider.datalink.schema.attachments;

import com.almworks.bugzilla.integration.data.*;
import com.almworks.util.tests.BaseTestCase;
import org.almworks.util.Failure;
import org.jetbrains.annotations.*;

import java.text.*;

public class AttachmentSubmitterTests extends BaseTestCase {
  private int nComments;
  private BugInfo info;
  private static final int ID = 239;

  private static final DateFormat MINUTES = new SimpleDateFormat("yyyy-MM-dd HH:mm");

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    nComments = 0;
    info = new BugInfo(null);
  }

  public void testSimple() {
    id("2009-12-24 03:54:19");
    assertEquals("u1", submitter("2009-12-24 03:54"));
  }

  public void testNoAttachment() {
    simple("2009-12-24 03:54:19");
    simple("2008-01-24 03:54:19");
    simple("2010-01-24 03:54:19");
    assertNull(submitter("2008-01-24 03:54:19"));
  }

  public void test4Comments() {
    simple("2010-02-11 20:10:00");
    simple("2010-02-11 20:10:12");
    id("2010-02-11 20:10:58");
    simple("2010-02-11 20:11:00");
    assertEquals("u3", submitter("2010-02-11 20:10"));
  }

  public void test2Ids() {
    simple("2010-02-11 20:10:00");
    simple("2010-02-11 20:10:12");
    id("2010-02-11 20:10:58");
    id("2010-02-11 20:11:00");
    assertEquals("u3", submitter("2010-02-11 20:10"));
  }

  public void test5Comments() {
    simple("2010-02-11 20:10:00");
    simple("2010-02-11 20:10:12");
    id("2010-02-11 20:10:58");
    id("2010-02-11 20:10:58");
    simple("2010-02-11 20:11:00");
    assertEquals("u3", submitter("2010-02-11 20:10"));
  }

  public void testManyCommentsManyIds() {
    simple("2010-02-01 20:10:00");
    simple("2010-02-02 20:10:00");
    simple("2010-02-03 20:10:00");
    simple("2010-02-04 20:10:00");
    simple("2010-02-05 20:10:00");
    simple("2010-02-06 20:10:00");
    simple("2010-02-07 20:10:00");
    simple("2010-02-08 20:10:00");
    simple("2010-02-09 20:10:00");
    simple("2010-02-10 20:10:00");
    simple("2010-02-11 20:10:00");
    simple("2010-02-12 20:10:00");
    simple("2010-02-13 20:10:00");
    id    ("2010-02-14 20:10:00");
    simple("2010-02-14 20:11:00");
    simple("2010-02-14 20:11:12");
    id    ("2010-02-14 20:11:30");
    id    ("2010-02-14 20:11:37");
    simple("2010-02-15 20:11:00");
    simple("2010-02-16 20:10:00");
    simple("2010-02-17 20:10:00");
    simple("2010-02-18 20:10:00");
    simple("2010-02-19 20:10:00");
    simple("2010-02-20 20:10:00");
    simple("2010-02-21 20:10:00");
    simple("2010-02-22 20:10:00");
    simple("2010-02-23 20:10:00");
    simple("2010-02-24 20:10:00");
    assertEquals("u17", submitter("2010-02-14 20:11"));
  }

  @Nullable
  private String submitter(String attachDate) {
    try {
      final BugzillaUser user = MatchAttachments.findSubmitterEmail(info, ID, MINUTES.parse(attachDate));
      return user == null ? null : user.getEmailId();
    } catch (ParseException e) {
      throw new Failure(e);
    }
  }

  private void simple(String time) {
    info.addComment(createComment(time, "comment #{0}"));
  }

  private void id(String time) {
    info.addComment(createComment(time, "comment #{0}: (id=" + ID + ")"));
  }

  private Comment createComment(String time, String textPattern) {
    ++nComments;
    String text = String.format(textPattern, String.valueOf(nComments));
    return new Comment(BugzillaUser.shortEmailName("u" + nComments, null, null), time, text, null, false,null);
  }
}
