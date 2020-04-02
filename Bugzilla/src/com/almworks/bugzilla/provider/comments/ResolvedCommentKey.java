package com.almworks.bugzilla.provider.comments;

import com.almworks.api.application.ItemOrder;
import com.almworks.api.application.UserChanges;
import org.jetbrains.annotations.*;

/**
 * @author : Dyoma
 */
public abstract class ResolvedCommentKey extends LoadedCommentKey {
  private final long myItem;
  private final long myConnection;
  private final ItemOrder myOrder;

  protected final int myIndex;

  protected ResolvedCommentKey(long item, int index, ItemOrder order, long connection) {
    myItem = item;
    myIndex = index;
    myOrder = order;
    myConnection = connection;
  }

  public long resolveOrCreate(UserChanges changes) {
    return myItem;
  }

  public int hashCode() {
    return (int)(myItem ^ (myItem >>> 32));
  }

  public boolean equals(Object obj) {
    if (obj instanceof ResolvedCommentKey) {
      return myItem == ((ResolvedCommentKey) obj).myItem;
    }
    return false;
  }

  public long getItem() {
    return myItem;
  }

  @Override
  public long getResolvedItem() {
    return myItem;
  }

  public long getConnection() {
    return myConnection;
  }

  public int getIndex() {
    return myIndex;
  }

  @NotNull
  public ItemOrder getOrder() {
    return myOrder;
  }

  public final boolean isFinal() {
    return getCommentPlace().isFinal();
  }
}
