package com.almworks.bugzilla.provider.datalink;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import com.almworks.bugzilla.integration.data.*;
import com.almworks.bugzilla.provider.BugzillaConnectionFixture;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.bugzilla.provider.datalink.schema.comments.DBComment;
import com.almworks.items.api.*;
import com.almworks.items.dp.DPEqualsIdentified;
import com.almworks.items.dp.DPNotNull;
import com.almworks.items.sync.*;
import com.almworks.items.sync.impl.SyncSchema;
import com.almworks.items.util.SyncAttributes;
import com.almworks.util.bool.BoolExpr;
import org.almworks.util.Collections15;

import java.util.*;

public class ComplexCommentsLinkTests extends BugzillaConnectionFixture {
  private CommentsLink myLink;
  public static final BugzillaUser USER1 = BugzillaUser.shortEmailName("u1", null, null);
  public static final BugzillaUser USER2 = BugzillaUser.shortEmailName("u2", null, null);
  public static final BugzillaUser USER3 = BugzillaUser.shortEmailName("u3", null, null);

  protected void setUp() throws Exception {
    super.setUp();
    myLink = metadata().commentsLink;
  }

  protected void tearDown() throws Exception {
    myLink = null;
    super.tearDown();
  }

  public void testUpdate() throws InterruptedException {
    checkUpdate(null, Collections.singletonList(new Comment(USER1, "20050101000021", "text 1", null, null, null)), 1);

    // NOT using same item twice in array
    checkUpdate(Arrays.asList(new Comment[] {
      new Comment(USER1, "20050101000001", "text 1", null, null, null),
    }), Arrays.asList(new Comment[] {
      new Comment(USER1, "20050101000001", "text 1", null, null, null),
      new Comment(USER1, "20050101000001", "text 1", null, null, null),
    }), 1);

    checkUpdate(Arrays.asList(new Comment[] {
      new Comment(USER1, "20050101000001", "text 1", null, null, null),
      new Comment(USER2, "20050101000101", "text 2", null, null, null),
      new Comment(USER2, "20070101000201", "text 3", null, null, null),
      new Comment(USER2, "20090101000201", "text 5", null, null, null),
      new Comment(USER3, "20100101000201", "text 6", null, null, null),
      new Comment(USER2, "20110101000201", "text 7", null, null, null),
      new Comment(USER1, "20130101000201", "text 9", null, null, null),
    }), Arrays.asList(new Comment[] {
      new Comment(USER1, "20050101000001", "text 1", null, null, null),
      new Comment(USER2, "20050101000101", "text 2", null, null, null),
      new Comment(USER1, "20060101000201", "text 3", null, null, null),
      new Comment(USER2, "20070101000201", "text 3", null, null, null),
      new Comment(USER1, "20080101000201", "text 4", null, null, null),
      new Comment(USER2, "20090101000201", "text 5", null, null, null),
      new Comment(USER3, "20100101000201", "text 6", null, null, null),
      new Comment(USER2, "20110101000201", "text 7", null, null, null),
      new Comment(USER3, "20120101000201", "text 8", null, null, null),
      new Comment(USER1, "20130101000201", "text 9", null, null, null),
      new Comment(USER2, "20140101000201", "text 0", null, null, null),
    }), 4);
  }

  private void checkUpdate(final List<Comment> initial, final List<Comment> update, final int count) throws InterruptedException {
    myManager.writeDownloaded(new DownloadProcedure<DBDrain>() {
      @Override
      public void write(DBDrain drain) throws DBOperationCancelledException {
        ItemVersionCreator creator = createEmptyBug(drain);
        setComments(creator, initial);
        checkUpdate2(creator, update, count);
      }

      @Override
      public void onFinished(DBResult<?> result) {
        assertTrue(result.isSuccessful());
      }
    }).waitForCompletion();
  }

  private int checkUpdate2(ItemVersionCreator creator, List<Comment> update, int count) {
    BugInfo info = createInfo(update, null, null);

    DBReader reader = creator.getReader();
    int initialCount = (int) reader.query(getServerCommentsExpr(privateMetadata())).count();

    myLink.updateRevision(privateMetadata(), creator, info, myContext);

    int newCount = (int) reader.query(getServerCommentsExpr(privateMetadata())).count();
    int added = newCount - initialCount;
    if (count >= 0)
      assertEquals(count, added);
    return added;
  }

  public static BoolExpr<DP> getServerCommentsExpr(PrivateMetadata privateMetadata) {
    return BoolExpr.and(
      DPEqualsIdentified.create(DBAttribute.TYPE, CommentsLink.typeComment),
      DPEqualsIdentified.create(SyncAttributes.CONNECTION, privateMetadata.thisConnection),
      DPNotNull.create(SyncSchema.BASE).negate());
  }

  private static BugInfo createInfo(List<Comment> comments, Comment removed, Comment added) {
    return createInfo(comments, removed, added, -1);
  }

  private static BugInfo createInfo(List<Comment> comments, Comment removed, Comment added, int addIndex) {
    BugInfo info = new BugInfo(null);
    List<Comment> list = Collections15.arrayList();
    list.addAll(comments);
    if (removed != null) {
      if (addIndex < 0)
        addIndex = list.indexOf(removed);
      list.remove(removed);
    }
    if (added != null) {
      if (addIndex >= 0)
        list.add(addIndex, added);
      else
        list.add(added);
    }
    info.setComments(list);
    info.getValues().put(BugzillaAttribute.ID, "239");
    return info;
  }

  private void setComments(ItemVersionCreator bug, List<Comment> comments) {
    if (comments != null) {
      for (int i = 0; i < comments.size(); i++) {
        Comment c = comments.get(i);
        DBComment.createNew(privateMetadata(), bug, i, c);
      }
    }
  }
}
