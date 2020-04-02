package com.almworks.bugzilla.integration.data;

import com.almworks.bugzilla.integration.BugzillaDateUtil;
import com.almworks.bugzilla.provider.BugzillaUtil;
import com.almworks.util.collections.Containers;
import org.almworks.util.Log;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.*;

public class Comment {
  public static final Comparator<Comment> TIME_ORDER = new Comparator<Comment>() {
    public int compare(Comment comment1, Comment comment2) {
      if (comment1 == comment2)
        return 0;
      long time1 = comment1.getWhenDate().getTime();
      long time2 = comment2.getWhenDate().getTime();
      return time1 < time2 ? -1 : (time1 > time2 ? 1 : 0);
    }
  };
  public static final Comparator<Comment> REVERSE_TIME_ORDER = Containers.reverse(TIME_ORDER);

  private final String myText;
  private final String myWhen;
  @Nullable
  private final BigDecimal myWorkTime;
  private final BugzillaUser myWho;
  private final TimeZone myDefaultTimezone;
  private final Boolean myPrivateComment;

  private Date myWhenDate = null;

  public Comment(BugzillaUser who, String when, String text, TimeZone defaultTimezone, Boolean privateComment,
    BigDecimal workTime)
  {
    myDefaultTimezone = defaultTimezone;
    myWho = who;
    myWhen = when;
    myWorkTime = workTime;
    myText = Util.NN(text);
    myPrivateComment = privateComment;
  }

  public Comment(Comment comment, FrontPageData.CommentInfo commentInfo) {
    myDefaultTimezone = comment.myDefaultTimezone;
    myWho = comment.myWho;
    myWhen = comment.myWhen;
    myText = comment.myText;
    myWhenDate = comment.myWhenDate;
    myWorkTime = comment.myWorkTime;
    myPrivateComment = commentInfo.privacy;
    if (!BugzillaUtil.commentDatesMatch(getWhenDate().getTime(), commentInfo.date)) {
      assert false : this + " " + commentInfo;
      Log.warn("comment date mismatch: " + getWhen() + " " + commentInfo);
    }
  }

  public String getText() {
    return myText;
  }

  @NotNull
  public synchronized Date getWhenDate() {
    if (myWhenDate == null)
      myWhenDate = BugzillaDateUtil.parseOrWarn(myWhen, myDefaultTimezone);
    return myWhenDate;
  }

  @Nullable
  public BugzillaUser getWho() {
    return myWho;
  }

  @Nullable
  public String getWhoEmail() {
    return myWho != null ? myWho.getEmailId() : null;
  }

  public String toString() {
    StringBuffer r = new StringBuffer("comment[");
    r.append(myWho).append(":").append(myWhen).append(":");
    if (myText.length() > 30)
      r.append(myText.substring(0, 27)).append("...");
    else
      r.append(myText);
    r.append(']');
    return r.toString();
  }

  private String getWhen() {
    return myWhen;
  }

  public String getWhenString() {
    return myWhen;
  }

  @Nullable
  public BigDecimal getWorkTime() {
    return myWorkTime;
  }

  public boolean isPrivacyKnown() {
    return myPrivateComment != null;
  }

  public boolean isPrivate() {
    return myPrivateComment != null && myPrivateComment;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    Comment comment = (Comment) o;

    if (myPrivateComment != null ? !myPrivateComment.equals(comment.myPrivateComment) :
      comment.myPrivateComment != null)
      return false;
    if (myText != null ? !myText.equals(comment.myText) : comment.myText != null)
      return false;
    if (myWhen != null ? !myWhen.equals(comment.myWhen) : comment.myWhen != null)
      return false;
    if (myWho != null ? !myWho.equals(comment.myWho) : comment.myWho != null)
      return false;
    if (myWorkTime != null ? !myWorkTime.equals(comment.myWorkTime) : comment.myWorkTime != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myText != null ? myText.hashCode() : 0;
    result = 31 * result + (myWhen != null ? myWhen.hashCode() : 0);
    result = 31 * result + (myWorkTime != null ? myWorkTime.hashCode() : 0);
    result = 31 * result + (myWho != null ? myWho.hashCode() : 0);
    result = 31 * result + (myPrivateComment != null ? myPrivateComment.hashCode() : 0);
    return result;
  }
}
