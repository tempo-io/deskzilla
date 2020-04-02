package com.almworks.bugzilla.gui.comments;

import com.almworks.api.actions.*;
import com.almworks.api.application.DBDataRoles;
import com.almworks.api.edit.ItemSyncSupport;
import com.almworks.api.edit.WindowItemEditor;
import com.almworks.api.engine.Connection;
import com.almworks.api.gui.BasicWindowBuilder;
import com.almworks.api.gui.DialogManager;
import com.almworks.bugzilla.provider.comments.LoadedCommentKey;
import com.almworks.bugzilla.provider.comments.ResolvedCommentKey;
import com.almworks.bugzilla.provider.datalink.schema.comments.CommentsLink;
import com.almworks.items.api.DBReader;
import com.almworks.items.sync.*;
import com.almworks.items.sync.util.AggregatingEditCommit;
import com.almworks.items.sync.util.SyncUtils;
import com.almworks.util.L;
import com.almworks.util.commons.FactoryE;
import com.almworks.util.images.Icons;
import com.almworks.util.ui.actions.*;

public class DeleteCommentAction extends SimpleAction {
  public static final AnAction INSTANCE = new DeleteCommentAction();

  public DeleteCommentAction() {
    super(L.actionName("Delete Comment"), Icons.ACTION_COMMENT_DELETE);
    setDefaultPresentation(PresentationKey.SHORT_DESCRIPTION, "Remove selected comment");
    setDefaultPresentation(PresentationKey.ENABLE, EnableState.ENABLED);
    watchRole(LoadedCommentKey.DATA_ROLE);
    watchModifiableRole(SyncManager.MODIFIABLE);
  }

  @Override
  protected void customUpdate(UpdateContext context) throws CantPerformException {
    DBDataRoles.checkAnyConnectionHasCapability(context, Connection.Capability.EDIT_ITEM);
    ResolvedCommentKey comment = BugzillaCommentsUtil.getComment(context);
    ItemActionUtils.checkCanEdit(context, comment.getItem(), comment.getConnection());
  }

  @Override
  protected void doPerform(ActionContext context) throws CantPerformException {
    ResolvedCommentKey comment = BugzillaCommentsUtil.getComment(context);
    EditControl control = ItemSyncSupport.prepareEditOrShowLockingEditor(context, comment);
    control.start(new MyHandler(control, comment.getItem(), comment.getIndex(), context.getSourceObject(DialogManager.ROLE)));
  }

  private static class MyHandler implements CommentEditorDialog.Handler, EditorFactory {
    private final EditControl myControl;
    private final long myCommentItem;
    private final int myIndex;
    private final DialogManager myDialogManager;

    private MyHandler(EditControl control, long commentItem, int index, DialogManager dialogManager) {
      myControl = control;
      myCommentItem = commentItem;
      myIndex = index;
      myDialogManager = dialogManager;
    }

    @Override
    public ItemEditor prepareEdit(DBReader reader, EditPrepare prepare) {
      final ItemVersion comment = SyncUtils.readTrunk(reader, myCommentItem);
      final String strId = BugzillaCommentsUtil.getBugId(comment);
      final String text = comment.getValue(CommentsLink.attrText);
      return new WindowItemEditor(prepare, new FactoryE<BasicWindowBuilder, CantPerformException>() {
        @Override
        public BasicWindowBuilder create() throws CantPerformException {
          return CommentEditorDialog.deleteComment(strId, "#" + myIndex, text, MyHandler.this, myDialogManager).getBuilder();
        }
      });
    }

    @Override
    public void onCommentEdited(boolean success, String commentText, boolean commitImmediately,
      CommentEditorDialog dialog)
    {
      if (!success) return;
      AggregatingEditCommit commit = new AggregatingEditCommit();
      if (commitImmediately) commit.addProcedure(null, UploadOnSuccess.create(myCommentItem));
      commit.addDelete(myCommentItem);
      myControl.commit(commit);
    }
  }
}
