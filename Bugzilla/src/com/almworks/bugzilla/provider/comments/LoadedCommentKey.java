package com.almworks.bugzilla.provider.comments;

import com.almworks.api.application.ItemKey;
import com.almworks.api.application.ItemOrder;
import com.almworks.api.application.viewer.Comment;
import com.almworks.util.collections.Convertor;
import com.almworks.util.ui.actions.DataRole;
import org.almworks.util.Collections15;
import org.almworks.util.Util;
import org.jetbrains.annotations.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * @author : Dyoma
 */
public abstract class LoadedCommentKey extends ItemKey implements Comment {
  public static final DataRole<LoadedCommentKey> DATA_ROLE = DataRole.createRole(LoadedCommentKey.class);
  public static final Convertor<Collection<? extends LoadedCommentKey>, Collection<? extends LoadedCommentKey>> GET_COMMENTS =
    new Convertor<Collection<? extends LoadedCommentKey>, Collection<? extends LoadedCommentKey>>() {
      public Collection<? extends LoadedCommentKey> convert(Collection<? extends LoadedCommentKey> value) {
        if (value == null)
          return null;
        LoadedCommentKey description = findDescription(value);
        if (description == null)
          return value;
        if (value.size() == 1) {
          assert Util.equals(value.iterator().next(), description) : description + ";" + value;
          return Collections15.emptyCollection();
        }
        List<LoadedCommentKey> result = Collections15.arrayList(value);
        boolean removed = result.remove(description);
        assert removed : description + " " + result;
        return result;
      }
    };

  public abstract int getIndex();

  public abstract CommentPlace getCommentPlace();

  public abstract boolean isFinal();

  @Nullable
  public abstract Boolean isPrivate();

  @Nullable
  public abstract BigDecimal getWorkTime();

  protected static ItemOrder localCommentOrder(int localIndex) {
    return ItemOrder.byOrder(10000000000L + localIndex);
  }

  @Nullable
  public static LoadedCommentKey findDescription(Collection<? extends LoadedCommentKey> comments) {
    if (comments == null)
      return null;
    int size = comments.size();
    if (size == 0)
      return null;
    boolean hasFinal = false;
    LoadedCommentKey firstNotFinal = null;
    for (LoadedCommentKey comment : comments) {
      if (comment.isFinal()) {
        if (comment.getIndex() == 0)
          return comment;
        hasFinal = true;
      } else if (firstNotFinal == null) {
        firstNotFinal = comment;
      }
    }
    return hasFinal ? null : firstNotFinal;
  }

  @Override
  public String getHeaderTooltipHtml() {
    return null;
  }

  public static final class CommentPlace {
    private final int myIntKey;

    private CommentPlace(int intKey) {
      myIntKey = intKey;
    }

    public boolean isFinal() {
      return myIntKey >= 0;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof CommentPlace))
        return false;
      return ((CommentPlace) obj).myIntKey == myIntKey;
    }

    public int hashCode() {
      return myIntKey;
    }

    public String toString() {
      return (isFinal() ? "Final" : "Local") + "[" + myIntKey + "]";
    }

    public static CommentPlace create(int intKey) {
      return new CommentPlace(intKey);
    }

    public static CommentPlace create(String id) {
      try {
        return id != null ? create(Integer.parseInt(id)) : null;
      } catch (NumberFormatException e) {
        return null;
      }
    }

    public boolean isBefore(CommentPlace other) {
      int otherKey = other.myIntKey;
      if (otherKey >= 0)
        return myIntKey < 0 ? false : myIntKey < otherKey;
      else
        return myIntKey < 0 ? myIntKey > otherKey : true;
    }

    public boolean isAfter(CommentPlace other) {
      int otherKey = other.myIntKey;
      if (otherKey >= 0)
        return myIntKey < 0 ? true : myIntKey > otherKey;
      else
        return myIntKey < 0 ? myIntKey < otherKey : false;
    }
  }
}
