package com.almworks.bugzilla.integration.oper;

import com.almworks.bugzilla.provider.BugzillaUtil;

import java.util.BitSet;

public abstract class CommentsMatcher {
  private final long myCommentDate;

  private int found = -1;
  private BitSet foundSet;

  protected CommentsMatcher(long sampleDate) {
    myCommentDate = sampleDate;
  }

  public final void acceptComment(int index, long date) {
    if (BugzillaUtil.commentDatesMatch(date, myCommentDate)) {
      if (found < 0) {
        found = index;
      } else {
        if (foundSet == null) {
          foundSet = new BitSet();
          foundSet.set(found);
        }
        foundSet.set(index);
      }
    }
  }

  public final int search() {
    if (found < 0)
      return found;
    if (foundSet != null) {
      for (int k = foundSet.nextSetBit(0); k >= 0; k = foundSet.nextSetBit(k + 1)) {
        if (compareToSample(k)) {
          found = k;
          break;
        }
      }
    }
    return found;
  }

  protected abstract boolean compareToSample(int index);
}
