package com.almworks.bugzilla.provider.comments;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ItemOrder;
import com.almworks.bugzilla.integration.BugzillaHTMLConstants;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import com.almworks.util.datetime.DateUtil;
import com.almworks.util.text.TextUtil;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author : Dyoma
 */
class FinalCommentKey extends ResolvedCommentKey implements ServerComment {
  private final ItemKey myWho;
  private final Date myWhen;
  private final String myHostedText;
  private final Boolean myPrivacy;
  private final BigDecimal myWorkTime;
  private String myText;

  FinalCommentKey(ItemKey whoString, Date when, String text, int index, long item, Boolean privacy, BigDecimal workTime, long connection) {
    super(item, index, ItemOrder.byOrder(index), connection);
    assert text != null;
    myHostedText = text;
    myWho = whoString;
    myWhen = when;
    myPrivacy = privacy;
    myWorkTime = workTime;
  }

  private String text() {
    if (myText == null) {
      if (myHostedText != null) {
        int length = BugzillaHTMLConstants.BUGZILLA_COMMENT_LINE - 5;
        if (length < 0) length = 75;
        myText = TextUtil.unwrapLines(myHostedText, length);
      }
    }
    return myText;
  }

  @Nullable
  public Boolean isPrivate() {
    return myPrivacy;
  }

  @Nullable
  public BigDecimal getWorkTime() {
    return myWorkTime;
  }

  public String toString() {
    return getDisplayName() + ": " + myText;
  }

  private String getAuthorEmail() {
    return myWho == null ? "" : myWho.getDisplayName();
  }

  private String formatWhen() {
    return DateUtil.toLocalDateTime(myWhen);
  }

  public Date getWhen() {
    return myWhen;
  }

  public void renderOn(Canvas canvas, CellState state) {
//    canvas.setHtml(formatWhen("yyyy-MM-dd HH:mm") + "<br>" + getAuthorEmail());
    canvas.appendText(formatWhen());
    canvas.newLine().appendText(getAuthorEmail());
  }

  public String getWhenText() {
    return formatWhen();
  }

  public String getWhoText() {
    return getAuthorEmail();
  }

  @NotNull
  public String getId() {
    return getAuthorEmail() + "#" + myIndex;
  }

  @NotNull
  public String getDisplayName() {
    return "#" + myIndex + " From " + getAuthorEmail() + " " + formatWhen();
  }

  public String getText() {
    return text();
  }

  public int getIndex() {
    return myIndex;
  }

  public LoadedCommentKey getCommentKey() {
    return this;
  }

  public CommentPlace getCommentPlace() {
    return CommentPlace.create(getIndex());
  }

  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    if (!super.equals(o))
      return false;

    FinalCommentKey that = (FinalCommentKey) o;

    if (myPrivacy != null ? !myPrivacy.equals(that.myPrivacy) : that.myPrivacy != null)
      return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myPrivacy != null ? myPrivacy.hashCode() : 0);
    return result;
  }
}
