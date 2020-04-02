package com.almworks.bugzilla.gui.comments;

import com.almworks.api.actions.CommentEditorDialog;
import com.almworks.api.application.DBDataRoles;
import com.almworks.api.application.ItemWrapper;
import com.almworks.api.engine.Connection;
import com.almworks.bugzilla.provider.BugzillaConnection;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.comments.ServerComment;
import com.almworks.util.L;
import com.almworks.util.images.Icons;
import com.almworks.util.text.LineTokenizer;
import com.almworks.util.ui.actions.*;

public class ReplyToCommentEditAction extends SimpleAction {
  public static final AnAction INSTANCE = new ReplyToCommentEditAction();

  public ReplyToCommentEditAction() {
    super(L.actionName("Reply to Comment\u2026"), Icons.ACTION_COMMENT_REPLY);
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, L.tooltip("Reply to the selected comment"));
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.ENABLED);
    watchRole(LoadedCommentKey.DATA_ROLE);
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    ItemWrapper bug = context.getSourceObject(ItemWrapper.ITEM_WRAPPER);
    DBDataRoles.checkExisting(bug);
    BugzillaConnection conn = CantPerformException.cast(BugzillaConnection.class, bug.getConnection());
    CantPerformException.ensure(conn.hasCapability(Connection.Capability.EDIT_ITEM));
    if (context.isDisabled())
      return;
    LoadedCommentKey comment = context.getSourceObject(LoadedCommentKey.DATA_ROLE);
    if (!(comment instanceof ServerComment))
      context.setEnabled(EnableState.DISABLED);
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    LoadedCommentKey comment = context.getSourceObject(LoadedCommentKey.DATA_ROLE);
    if (!(comment instanceof ServerComment))
      throw new CantPerformException("comment is not uploaded to server");

    ServerComment serverComment = (ServerComment) comment;
    String initialText = getInitialText(serverComment);
    int commentIndex = serverComment.getIndex();

    String title = commentIndex == 0 ? "Description" : "Comment " + commentIndex;
    String id = AddCommentAction.getBugId(context);
    AddCommentAction.MyHandler handler = AddCommentAction.MyHandler.create(context);
    CommentEditorDialog dialog = CommentEditorDialog.replyComment(context, id, initialText, title, handler, handler.getCheckBox());
    dialog.getBuilder().showWindow();
  }

  private String getInitialText(ServerComment comment) {
    String text = comment.getCommentKey().getText();
    StringBuffer buffer = new StringBuffer();
    buffer.append("(In reply to ");
    int index = comment.getIndex();
    if (index == 0) {
      buffer.append("description");
    } else {
      buffer.append("comment #").append(index);
    }
    buffer.append(")\n");
    LineTokenizer.prependLines(text, "> ", buffer);
    int length = buffer.length();
    if (length < 2 || buffer.charAt(length - 1) != '\n' || buffer.charAt(length - 2) != '\n')
      buffer.append('\n');
    return buffer.toString();
  }
}
