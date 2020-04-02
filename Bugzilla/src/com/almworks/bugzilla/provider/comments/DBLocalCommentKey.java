package com.almworks.bugzilla.provider.comments;

import com.almworks.api.application.UserChanges;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.L;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.awt.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author : Dyoma
 */
class DBLocalCommentKey extends ResolvedCommentKey implements LocalLoadedComment {
  private final String myOriginalText;
  private final Boolean myOriginalPrivacy;
  private final BigDecimal myOriginalWorkTime;
  private final int myIndex;
  private final int myLocalIndex;

  private String myText;
  private Boolean myPrivacy;
  private BigDecimal myWorkTime;

  public DBLocalCommentKey(long item, String text, int index, int localIndex, Boolean privacy, BigDecimal workTime, long connection) {
    super(item, index, localCommentOrder(localIndex), connection);
    assert localIndex > 0 : localIndex;
    String s = Util.NN(text);
    myIndex = index;
    myLocalIndex = localIndex;
    myOriginalText = myText = s;
    myOriginalPrivacy = myPrivacy = privacy;
    myOriginalWorkTime = myWorkTime = workTime;
  }

  @NotNull
  public String getId() {
    return myIndex == 0 ? L.textTitle("Description") : L.textTitle("New Comment #" + myIndex);
  }

  @Nullable
  public Boolean isPrivate() {
    return myPrivacy;
  }

  public void setWorkTime(BigDecimal workTime) {
    myWorkTime = workTime;
  }

  @Override
  public BigDecimal getWorkTime() {
    return myWorkTime;
  }

  public void setPrivacy(Boolean privacy) {
    myPrivacy = privacy;
  }

  @NotNull
  public String getDisplayName() {
    return L.textTitle("New Comment");
  }

  public long resolveOrCreate(UserChanges changes) {
    long commentItem = getItem();
    if(isChanged()) {
      ItemVersionCreator creator = changes.getCreator().changeItem(commentItem);
      creator.setValue(CommentsLink.attrText, getText());
      if (myPrivacy != null) {
        creator.setValue(CommentsLink.attrPrivate, myPrivacy);
      }
      if (myWorkTime != null) {
        creator.setValue(CommentsLink.attrWorkTime, myWorkTime);
      }
    }
    return commentItem;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof DBLocalCommentKey)) return false;
    DBLocalCommentKey that = (DBLocalCommentKey) obj;
    return
      getResolvedItem() == that.getResolvedItem() &&
      Util.equals(myText, that.myText) &&
      Util.equals(myPrivacy, that.myPrivacy);
  }

  public void renderOn(Canvas canvas, CellState state) {
    canvas.appendText(L.content("New comment"));
    canvas.newLine().appendText(L.content("<Not uploaded>"));
//    canvas.setHtml(L.html("New comment<br>&lt;Not uploaded&gt;"));
    canvas.setFontStyle(Font.ITALIC);
  }

  public String getWhenText() {
    return "";
  }

  @Nullable
  public Date getWhen() {
    return null;
  }

  public String getWhoText() {
    return "<new comment>";
  }

  public void setText(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  public int getIndex() {
    return myIndex;
  }

  public CommentPlace getCommentPlace() {
    return CommentPlace.create(-myLocalIndex);
  }

  public boolean isChanged() {
    return !myOriginalText.equals(myText) || !Util.equals(myOriginalPrivacy, myPrivacy) || !Util.equals(myOriginalWorkTime, myWorkTime);
  }

  public int getLocalIndex() {
    return myLocalIndex;
  }

  public LoadedCommentKey getCommentKey() {
    return this;
  }
}
