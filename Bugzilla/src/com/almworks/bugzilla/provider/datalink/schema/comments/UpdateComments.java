package com.almworks.bugzilla.provider.datalink.schema.comments;

import com.almworks.bugzilla.integration.data.Comment;
import com.almworks.bugzilla.provider.BugzillaContext;
import com.almworks.items.sync.ItemVersionCreator;
import org.almworks.util.*;

import java.util.Iterator;
import java.util.List;

class UpdateComments {
  private final BugzillaContext myContext;
  private final ItemVersionCreator myBug;
  private final Comment[] myServerComments;
  private final String myBugId;
  private final List<DBComment> myUnmatchedDB = Collections15.arrayList();
  private final List<Comment> myUnmatchedServer;
  private Boolean myUserSeesPrivateComments;

  public UpdateComments(BugzillaContext context, ItemVersionCreator bug, Comment[] comments, String bugId,
    Boolean userSeesPrivateComments) {
    myContext = context;
    myBug = bug;
    myServerComments = comments;
    myUnmatchedServer = Collections15.arrayList(comments);
    myBugId = bugId;
    myUserSeesPrivateComments = userSeesPrivateComments;
  }

  public void perform() {
    DBComment.loadAll(myBug, myContext.getPrivateMetadata(), myUnmatchedDB);
    int dbIndex = 0;
    for (int i = 0, commentsSize = myServerComments.length; i < commentsSize; i++) {
      Comment comment = myServerComments[i];
      if (comment.isPrivacyKnown() && comment.isPrivate()) myUserSeesPrivateComments = true;
      if (dbIndex < myUnmatchedDB.size()) {
        DBComment dbComment = myUnmatchedDB.get(dbIndex);
        if (dbComment.matches(comment, true, false)) {
          dbComment.update(comment, i);
          myUnmatchedDB.remove(dbIndex);
          myUnmatchedServer.remove(comment);
        } else dbIndex++;
      }
    }
    matchComments(true, false);
    matchComments(false, false);
    matchComments(true, true);
    for (Comment comment : myUnmatchedServer)
      DBComment.createNew(myContext.getPrivateMetadata(), myBug, getServerIndex(comment), comment);
    for (DBComment dbComment : myUnmatchedDB) dbComment.delete();
    if (myUserSeesPrivateComments != null) myContext.setCommentPrivacyAccessible(myUserSeesPrivateComments, "bug #" + myBugId);
  }

  private void matchComments(boolean matchText, boolean matchNew) {
    for (Iterator<Comment> iterator = myUnmatchedServer.iterator(); iterator.hasNext();) {
      if (myUnmatchedDB.isEmpty()) return;
      Comment comment = iterator.next();
      int dbIndex = DBComment.find(myUnmatchedDB, comment, matchText, matchNew);
      if (dbIndex >= 0) {
        int index = getServerIndex(comment);
        myUnmatchedDB.remove(dbIndex).update(comment, index);
        iterator.remove();
      }
    }
  }

  private int getServerIndex(Comment comment) {
    int index = ArrayUtil.indexOf(myServerComments, comment);
    if (index < 0) Log.error("Comment not found " + comment);
    return index;
  }
}
