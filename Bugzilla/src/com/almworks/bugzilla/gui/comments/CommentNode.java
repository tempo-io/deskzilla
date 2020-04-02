package com.almworks.bugzilla.gui.comments;

import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.util.components.TreeModelBridge;

/**
 * @author dyoma
*/
class CommentNode extends TreeModelBridge<LoadedCommentKey> {
  public CommentNode(LoadedCommentKey comment) {
    super(comment);
  }
}
