package com.almworks.bugzilla.provider.comments;

import com.almworks.util.commons.Condition;

/**
 * @author : Dyoma
 */
public interface ServerComment {
  Condition<LoadedCommentKey> SERVER_COMMENT = new Condition<LoadedCommentKey>() {
    public boolean isAccepted(LoadedCommentKey value) {
      return value instanceof ServerComment;
    }
  };

  int getIndex();

  LoadedCommentKey getCommentKey();
}
