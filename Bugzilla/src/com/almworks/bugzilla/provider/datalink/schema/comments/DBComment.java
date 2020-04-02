package com.almworks.bugzilla.provider.datalink.schema.comments;

import com.almworks.bugzilla.integration.data.Comment;
import com.almworks.bugzilla.provider.PrivateMetadata;
import com.almworks.bugzilla.provider.datalink.LoadedObject;
import com.almworks.bugzilla.provider.datalink.schema.User;
import com.almworks.items.sync.*;
import org.almworks.util.Util;

import java.util.*;

public class DBComment {
  private static final LoadedObject.AttributeSet ATTRS = new LoadedObject.AttributeSet(CommentsLink.typeComment,
    CommentsLink.attrMaster)
    .addAttribute(CommentsLink.attrText, "")
    .addAttribute(CommentsLink.attrIndex, -1)
    .addAttribute(CommentsLink.attrDate, new Date(0))
    .addItemReference(CommentsLink.attrAuthor).fix();
  private static final Comparator<DBComment> BY_INDEX = new Comparator<DBComment>() {
    @Override
    public int compare(DBComment o1, DBComment o2) {
      if (o1 == o2) return 0;
      if (o1 == null || o2 == null) return o1 == null ? 1 : -1;
      int index1 = o1.myDBComment.getValue(CommentsLink.attrIndex);
      int index2 = o2.myDBComment.getValue(CommentsLink.attrIndex);
      return index1 - index2;
    }
  };

  private final PrivateMetadata myPM;
  private final LoadedObject.Writable myDBComment;
  private String myAuthorId = null;

  private DBComment(LoadedObject.Writable comment,PrivateMetadata pm) {
    myDBComment = comment;
    myPM = pm;
  }

  public static void loadAll(ItemVersionCreator bug, PrivateMetadata pm, List<DBComment> target) {
    assert target.isEmpty() : target;
    for (ItemVersionCreator comment : bug.changeItems(bug.getSlaves(CommentsLink.attrMaster))) {
      target.add(load(comment, pm));
    }
    Collections.sort(target, BY_INDEX);
    int countDown = target.size();
    while (countDown > 0) {
      countDown--;
      DBComment comment = target.get(0);
      if (comment.myDBComment.getValue(CommentsLink.attrIndex) < 0) {
        target.remove(0);
        target.add(comment);
      } else break;
    }
  }

  private static DBComment load(ItemVersionCreator comment, PrivateMetadata pm) {
    LoadedObject.Writable server = ATTRS.changeServer(comment);
    return new DBComment(server, pm);
  }

  public void delete() {
    if (myDBComment.isDeleted()) return;
    if (myDBComment.getSyncState() == SyncState.NEW) return;
    myDBComment.delete();
  }

  public static int find(List<DBComment> db, Comment server, boolean matchText, boolean matchNew) {
    for (int i = 0, dbSize = db.size(); i < dbSize; i++)
      if (db.get(i).matches(server, true, matchNew)) return i;
    if (!matchText)
      for (int i = 0, dbSize = db.size(); i < dbSize; i++) {
        if (db.get(i).matches(server, false, matchNew)) return i;
      }
    return -1;
  }

  public long getItem() {
    return myDBComment.getItem();
  }

  boolean matches(Comment server, boolean matchText, boolean matchNew) {
    boolean isNew = myDBComment.getSyncState() == SyncState.NEW;
    if (isNew && !matchNew) return false;
    if (isNew) matchText = true;
    if (matchText) {
      String serverText = server.getText().replaceAll("\\s", "");
      String localText = Util.NN(myDBComment.getValue(CommentsLink.attrText), "").replaceAll("\\s", "");
      if (!localText.equals(serverText)) return false;
    }
    if (myDBComment.getSyncState() != SyncState.NEW) {
      Date when = server.getWhenDate();
      Date dbWhen = myDBComment.getValue(CommentsLink.attrDate);
      if (!Util.equals(when, dbWhen))return false;
    }
    String authorId = getAuthorId();
    if (myDBComment.getSyncState() == SyncState.NEW) {
      if (authorId == null || authorId.trim().length() == 0) {
        authorId = myPM.getThisUserId();
      }
    }
    return Util.equals(server.getWhoEmail(), authorId == null || authorId.isEmpty() ? null : authorId);
  }

  public void update(Comment comment, int index) {
    String text = comment.getText();
    myDBComment.setAlive();
    myDBComment.setValue(CommentsLink.attrText, text);
    myDBComment.setValue(CommentsLink.attrIndex, index);
    if (comment.isPrivacyKnown()) myDBComment.setValue(CommentsLink.attrPrivate, comment.isPrivate());
    myDBComment.setValue(CommentsLink.attrDate, comment.getWhenDate());
    DBDrain drain = myDBComment.getDrain();
    long who = User.getOrCreate(drain, comment.getWho(), myPM);
    if (who > 0) {
      myDBComment.setValue(CommentsLink.attrAuthor, who);
      myAuthorId = User.getRemoteId(drain.forItem(who));
    }
    myDBComment.setValue(CommentsLink.attrWorkTime, comment.getWorkTime());
  }

  private String getAuthorId() {
    if (myAuthorId == null) {
      Long author = myDBComment.getValue(CommentsLink.attrAuthor);
      if (author > 0) myAuthorId = User.getRemoteId(myDBComment.forItem(author));
    }
    return myAuthorId;
  }

  public static DBComment createNew(PrivateMetadata pm, ItemVersionCreator bug, int index, Comment comment) {
    LoadedObject.Writable loadedComment = ATTRS.newSlave(bug);
    DBComment created = new DBComment(loadedComment, pm);
    created.update(comment, index);
    return created;
  }
}
