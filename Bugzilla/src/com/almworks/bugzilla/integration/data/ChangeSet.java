package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaAttribute;
import org.almworks.util.Collections15;
import org.jetbrains.annotations.*;

import java.util.Date;
import java.util.List;

/**
 * :todoc:
 *
 * @author sereda
 */
public class ChangeSet {
  private final List<Change> myChanges = Collections15.arrayList();
  private final Date myWhen;
  private final BugzillaUser myWho;
  private final Comment myComment;

  public ChangeSet(BugzillaUser who, Date when) {
    assert who != null;
    assert when != null;
    myWho = who;
    myWhen = when;
    myComment = null;
  }

  public ChangeSet(Comment comment) {
    myWho = comment.getWho();
    myWhen = comment.getWhenDate();
    myComment = comment;
  }

  public ChangeSet(ChangeSet source, Comment comment) {
    myWho = source.myWho;
    myWhen = source.myWhen;
    myChanges.addAll(source.myChanges);
    myComment = comment;
  }

  public Date getWhen() {
    return myWhen;
  }

  @Nullable
  public BugzillaUser getWho() {
    return myWho;
  }

  public void addChange(Change change) {
    myChanges.add(change);
  }

  public List<Change> getChanges() {
    return myChanges;
  }

  public Comment getComment() {
    return myComment;
  }

  public static class Change {
    public final String myAdded;
    public final String myRemoved;
    public final BugzillaAttribute what;

    public Change(BugzillaAttribute what, String removed, String added) {
      this.what = what;
      this.myRemoved = removed == null ? "" : removed.trim();
      this.myAdded = added == null ? "" : added.trim();
    }

    public String getRemoved() {
      return myRemoved;
    }

    public String getAdded() {
      return myAdded;
    }
  }
}