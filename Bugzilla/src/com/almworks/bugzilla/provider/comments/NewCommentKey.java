package com.almworks.bugzilla.provider.comments;

import com.almworks.api.application.ItemOrder;
import com.almworks.api.application.UserChanges;
import com.almworks.api.explorer.gui.ResolverItemFactory;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.items.sync.ItemVersionCreator;
import com.almworks.util.L;
import com.almworks.util.components.Canvas;
import com.almworks.util.components.renderer.CellState;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author : Dyoma
 */
class NewCommentKey extends LoadedCommentKey implements LocalLoadedComment {
  private final ResolverItemFactory myResolver;

  @NotNull
  private String myText = "";

  private final int myLocalIndex;

  private final ItemOrder myOrder;

  @Nullable
  private Boolean myPrivacy;

  @Nullable
  private BigDecimal myWorkTime;

  NewCommentKey(ResolverItemFactory resolver, int localIndex) {
    //todo:add parameter "text", remove text setter
    assert localIndex > 0 : localIndex;
    myResolver = resolver;
    myLocalIndex = localIndex;
    myOrder = localCommentOrder(localIndex);
  }

  public void setText(@NotNull String text) {
    myText = text;
  }

  public void setPrivacy(Boolean privacy) {
    myPrivacy = privacy;
  }

  public void setWorkTime(BigDecimal workTime) {
    myWorkTime = workTime;
  }

  public boolean isChanged() {
    return true;
  }

  public int getLocalIndex() {
    return myLocalIndex;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public long resolveOrCreate(UserChanges changes) {
    long item = myResolver.createItem(getText(), changes);
    if (myPrivacy != null) {
      ItemVersionCreator creator = changes.getCreator().changeItem(item);
      creator.setValue(CommentsLink.attrPrivate, myPrivacy);
    }
    if (myWorkTime != null) {
      ItemVersionCreator creator = changes.getCreator().changeItem(item);
      creator.setValue(CommentsLink.attrWorkTime, myWorkTime);
    }
    return item;
  }

  @Nullable
  public Boolean isPrivate() {
    return myPrivacy;
  }

  @Override
  public BigDecimal getWorkTime() {
    return myWorkTime;
  }

  @NotNull
  public String getId() {
    return myText;
  }

  @NotNull
  public String getDisplayName() {
    return L.textTitle("New Comment");
  }

  public void renderOn(Canvas canvas, CellState state) {
//    canvas.setHtml(L.html("New comment<br>&lt;Not uploaded&gt;"));
    canvas.appendText("New comment");
    canvas.newLine().appendText("<Not uploaded>");
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

  public int getIndex() {
    return -1;
  }

  public CommentPlace getCommentPlace() {
    return CommentPlace.create(-myLocalIndex);
  }

  public boolean isFinal() {
    return getCommentPlace().isFinal();
  }

  @NotNull
  public ItemOrder getOrder() {
    return myOrder;
  }

  public LoadedCommentKey getCommentKey() {
    return this;
  }
}
